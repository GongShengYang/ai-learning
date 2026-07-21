package com.gsy.ai.document.rag.parser;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Component
public class TxtParser implements DocumentParser {

    @Override
    public String parse(MultipartFile file) throws Exception {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public boolean supports(String fileType) {
        return "txt".equalsIgnoreCase(fileType) || "md".equalsIgnoreCase(fileType);
    }
}