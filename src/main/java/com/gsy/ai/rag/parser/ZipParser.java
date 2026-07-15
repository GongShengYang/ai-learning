package com.gsy.ai.rag.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ZipParser implements DocumentParser {


    @Override
    public String parse(MultipartFile file) throws Exception {
        // 对于 ZIP，我们不返回单个文本，而是解压后逐个解析内部文件
        // 但接口设计为返回 String 不合适，这里抛出异常说明用专用方法
        throw new UnsupportedOperationException("ZIP 请使用 parseZip 方法");
    }

    /**
     * 解压 ZIP，返回其中所有支持文件的文本内容（按文件名拼接）
     */
    // ZipParser.java
    public List<UnpackedFile> unpack(byte[] fileBytes) throws Exception {
        List<UnpackedFile> result = new ArrayList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
             ZipArchiveInputStream zis = new ZipArchiveInputStream(bais)) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextZipEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
                if (!isSupportedExt(ext)) {
                    log.warn("ZIP中跳过不支持的文件: {}", name);
                    continue;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                result.add(new UnpackedFile(name, baos.toByteArray()));
            }
        }
        return result;
    }

    // 原有 unpack(MultipartFile) 可以保留，但改为调用新方法
    public List<UnpackedFile> unpack(MultipartFile zipFile) throws Exception {
        return unpack(zipFile.getBytes()); // 但仍可能失败，建议新调用直接使用 byte[]
    }

    private boolean isSupportedExt(String ext) {
        return "txt".equals(ext) || "md".equals(ext) || "doc".equals(ext) ||
                "docx".equals(ext) || "pdf".equals(ext);
    }

    @Override
    public boolean supports(String fileType) {
        return "zip".equalsIgnoreCase(fileType);
    }

    public static class UnpackedFile {
        private final String fileName;
        private final byte[] content;

        public UnpackedFile(String fileName, byte[] content) {
            this.fileName = fileName;
            this.content = content;
        }
        public String getFileName() { return fileName; }
        public byte[] getContent() { return content; }
    }
}