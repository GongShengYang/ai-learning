package com.gsy.ai.rag.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.gson.Gson;
import com.gsy.ai.entity.DocumentChunkDO;
import com.gsy.ai.mapper.DocumentChunkMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Primary
@Component
public class DatabaseVectorStore implements VectorStore {

    private final DocumentChunkMapper documentChunkMapper;
    private final Gson gson = new Gson();

    public DatabaseVectorStore(DocumentChunkMapper documentChunkMapper) {
        this.documentChunkMapper = documentChunkMapper;
    }

    /**
     * 当前阶段不在这里写入数据库。
     *
     * 原因：
     * 现在 DocumentImportService 已经通过 documentChunkService.saveBatch()
     * 把 Chunk 和 vector 保存到数据库了。
     *
     * 如果这里再 insert，会导致重复数据。
     *
     * 后续如果重构为：
     * DocumentImportService -> VectorStore.add()
     * 那时候再把 add() 改成真正入库。
     */
    @Override
    public void add(ChunkWithVector chunk) {
        log.debug("DatabaseVectorStore.add 当前阶段不执行入库，避免重复保存");
    }

    /**
     * 从数据库检索 TopK 相似 Chunk
     */
    @Override
    public List<ChunkWithVector> search(float[] queryVector, Long documentId, int topK) {
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }

        if (topK <= 0) {
            return List.of();
        }

        LambdaQueryWrapper<DocumentChunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(DocumentChunkDO::getVector)
                .ne(DocumentChunkDO::getVector, "");
        // 有documentId
        if(documentId != null){
            wrapper.eq(DocumentChunkDO::getDocumentId, documentId);
        }

        List<DocumentChunkDO> chunkList = documentChunkMapper.selectList(wrapper);

        if (chunkList == null || chunkList.isEmpty()) {
            log.info("数据库中没有可检索的文档切片");
            return List.of();
        }

        List<ScoreItem> scoreItems = new ArrayList<>();

        for (DocumentChunkDO chunkDO : chunkList) {
            try {
                float[] chunkVector = gson.fromJson(chunkDO.getVector(), float[].class);

                if (chunkVector == null || chunkVector.length == 0) {
                    continue;
                }

                float score = cosineSimilarity(queryVector, chunkVector);

                scoreItems.add(new ScoreItem(chunkDO, chunkVector, score));
            } catch (Exception e) {
                log.warn("解析Chunk向量失败，chunkId: {}, documentId: {}, 原因: {}", chunkDO.getId(), chunkDO.getDocumentId(), e.getMessage());
            }
        }

        if (scoreItems.isEmpty()) {
            return List.of();
        }

        scoreItems.sort(Comparator.comparing(ScoreItem::getScore).reversed());

        List<ChunkWithVector> result = new ArrayList<>();

        for (int i = 0; i < Math.min(topK, scoreItems.size()); i++) {
            ScoreItem item = scoreItems.get(i);
            DocumentChunkDO chunkDO = item.getChunkDO();

            result.add(new ChunkWithVector(chunkDO.getContent(), item.getVector(), chunkDO.getDocumentId(), chunkDO.getChunkIndex(), item.getScore()
            ));
        }

        log.info("数据库向量检索完成，总Chunk数:{}, 返回TopK:{}", chunkList.size(), result.size());


        for (ChunkWithVector chunk : result) {
            log.info("命中文档 documentId={}, chunkIndex={}, score={}", chunk.getDocumentId(), chunk.getChunkIndex(), chunk.getScore());
        }
        return result;
    }

    /**
     * 当前阶段不建议直接清空数据库数据。
     */
    @Override
    public void clear() {
        log.warn("DatabaseVectorStore.clear 当前阶段不执行，避免误删数据库文档切片");
    }

    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0;
        }

        float dot = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dot / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Data
    @AllArgsConstructor
    private static class ScoreItem {
        private DocumentChunkDO chunkDO;
        private float[] vector;
        private float score;
    }
}