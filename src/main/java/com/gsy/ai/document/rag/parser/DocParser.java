package com.gsy.ai.document.rag.parser;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocParser implements DocumentParser {

    @Override
    public String parse(MultipartFile file) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(file.getInputStream())) {
            WordExtractor extractor = new WordExtractor(doc);
            return extractor.getText();
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "doc".equalsIgnoreCase(fileType);
    }
}