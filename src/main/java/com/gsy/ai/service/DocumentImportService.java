package com.gsy.ai.service;

import com.gsy.ai.common.BusinessException;
import com.gsy.ai.entity.DocumentChunkDO;
import com.gsy.ai.entity.DocumentDO;
import com.gsy.ai.mapper.DocumentMapper;
import com.gsy.ai.rag.chunk.ChunkStrategy;
import com.gsy.ai.rag.embedding.EmbeddingService;
import com.gsy.ai.rag.parser.*;
import com.google.gson.Gson;
import com.gsy.ai.rag.store.ChunkWithVector;
import com.gsy.ai.rag.store.MilvusVectorStore;
import com.gsy.ai.rag.store.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DocumentImportService {

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private DocumentChunkService documentChunkService;

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private ChunkStrategy chunkStrategy;

    @Resource
    private List<DocumentParser> parsers; // 自动注入所有Parser

    @Value("${gsy.file.upload-path}")
    private String uploadPath;

    @Resource
    private VectorStore vectorStore;

    private final Gson gson = new Gson();

    @Transactional
    public Long importDocument(MultipartFile file) throws Exception {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }
        String ext = originalName.contains(".") ?
                originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase() : "";

        // 1. 保存物理文件
        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);
        String uniqueName = System.currentTimeMillis() + "_" + originalName;
        Path targetPath = uploadDir.resolve(uniqueName);
        file.transferTo(targetPath.toFile());

        // ----- 新增：从已保存文件读取字节数组，作为后续数据源 -----
        byte[] fileBytes = Files.readAllBytes(targetPath);
        // 构造内存中的 MultipartFile（用于其他 Parser）
        MultipartFile memoryFile = new ByteArrayMultipartFile(originalName, fileBytes);

        // 2. 创建 Document 记录（使用原始 file 的大小）
        DocumentDO doc = new DocumentDO();
        doc.setFileName(originalName);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setFilePath(targetPath.toString());
        doc.setStatus(0);
        documentMapper.insert(doc);
        Long docId = doc.getId();

        try {
            // 3. 解析文本
            doc.setStatus(1); // 解析中
            documentMapper.updateById(doc);

            String fullText = extractText(memoryFile, ext);   // 注意此处传 memoryFile
            if (fullText == null || fullText.trim().isEmpty()) {
                throw new BusinessException("文件内容为空");
            }

            // 4. 切片
            List<ChunkStrategy.ChunkResult> chunkResults = chunkStrategy.split(fullText);
            if (chunkResults.isEmpty()) {
                throw new BusinessException("切片结果为空");
            }

            // 5. 批量 Embedding
            doc.setStatus(2); // Embedding中
            documentMapper.updateById(doc);

            List<String> texts = chunkResults.stream()
                    .map(ChunkStrategy.ChunkResult::getContent)
                    .toList();
            List<float[]> vectors = embeddingService.embedBatch(texts);

            // 6. 保存Chunk + 向量
            List<DocumentChunkDO> chunkDOs = new ArrayList<>();
            for (int i = 0; i < chunkResults.size(); i++) {
                ChunkStrategy.ChunkResult cr = chunkResults.get(i);
                float[] vec = vectors.get(i);

                DocumentChunkDO chunkDO = new DocumentChunkDO();
                chunkDO.setDocumentId(docId);
                chunkDO.setChunkIndex(i);
                chunkDO.setContent(cr.getContent());
                chunkDO.setVector(gson.toJson(vec));
                chunkDO.setStartOffset(cr.getStartOffset());
                chunkDO.setEndOffset(cr.getEndOffset());
                chunkDOs.add(chunkDO);
            }

            boolean saved = documentChunkService.saveBatch(chunkDOs);

            if (!saved) {
                throw new BusinessException("文档切片保存失败");
            }

            List<ChunkWithVector> milvusChunks = new ArrayList<>(chunkDOs.size());

            for (int i = 0; i < chunkDOs.size(); i++) {

                DocumentChunkDO chunkDO = chunkDOs.get(i);

                if (chunkDO.getId() == null) {
                    throw new BusinessException(
                            "文档切片保存后主键未回填，chunkIndex="
                                    + chunkDO.getChunkIndex()
                    );
                }

                ChunkWithVector milvusChunk =
                        new ChunkWithVector(
                                chunkDO.getId(),
                                chunkDO.getContent(),
                                vectors.get(i),
                                chunkDO.getDocumentId(),
                                chunkDO.getChunkIndex(),
                                null
                        );

                milvusChunks.add(milvusChunk);
            }

            vectorStore.addBatch(milvusChunks);

            // 7. 更新Document状态
            doc.setChunkCount(chunkDOs.size());
            doc.setStatus(3); // 成功
            doc.setRemark("导入成功");
            documentMapper.updateById(doc);

            return docId;

        } catch (Exception e) {
            log.error("文档导入失败: ", e);
            doc.setStatus(4); // 失败
            doc.setRemark(e.getMessage());
            documentMapper.updateById(doc);
            throw e;
        }
    }

    private String extractText(MultipartFile file, String ext) throws Exception {
        // 对ZIP特殊处理
        if ("zip".equalsIgnoreCase(ext)) {
            // 从 file 获取字节数组（但此时 file 是内存版，不会有临时文件问题）
            // 或者更好，直接让 extractText 接受 byte[]，但为保持现有调用，暂时用 file.getBytes()
            // 但 file 是 ByteArrayMultipartFile，getBytes() 返回内存字节，安全。
            return extractZipText(file.getBytes());
        }

        // 普通文件：找对应Parser
        for (DocumentParser parser : parsers) {
            if (parser.supports(ext)) {
                return parser.parse(file);
            }
        }
        throw new BusinessException("不支持的文件格式: " + ext);
    }

    private String extractZipText(byte[] zipBytes) throws Exception {
        ZipParser zipParser = null;
        for (DocumentParser p : parsers) {
            if (p instanceof ZipParser) {
                zipParser = (ZipParser) p;
                break;
            }
        }
        if (zipParser == null) {
            throw new BusinessException("未找到ZIP解析器");
        }

        // 使用新的 unpack(byte[]) 方法
        List<ZipParser.UnpackedFile> unpacked = zipParser.unpack(zipBytes);
        if (unpacked.isEmpty()) {
            throw new BusinessException("ZIP文件中没有支持的文件");
        }

        StringBuilder fullText = new StringBuilder();
        for (ZipParser.UnpackedFile uf : unpacked) {
            String name = uf.getFileName();
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
            // 用字节内容构建内存 MultipartFile
            MultipartFile mockFile = new ByteArrayMultipartFile(uf.getFileName(), uf.getContent());
            for (DocumentParser parser : parsers) {
                if (parser.supports(ext)) {
                    try {
                        String text = parser.parse(mockFile);
                        fullText.append("\n\n=== 文件: ").append(name).append(" ===\n");
                        fullText.append(text);
                    } catch (Exception e) {
                        log.warn("解析ZIP内文件失败: {}，原因: {}", name, e.getMessage());
                        fullText.append("\n\n=== 文件: ").append(name).append(" ===\n");
                        fullText.append("[解析失败: ").append(e.getMessage()).append("]");
                    }
                    break;
                }
            }
        }
        return fullText.toString();
    }

    // 内部辅助类：模拟MultipartFile
    static class ByteArrayMultipartFile implements MultipartFile {
        private final String name;
        private final byte[] content;

        ByteArrayMultipartFile(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "application/octet-stream"; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }
}