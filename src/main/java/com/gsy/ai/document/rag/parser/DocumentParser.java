package com.gsy.ai.document.rag.parser;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentParser {
    String parse(MultipartFile file) throws Exception;
    boolean supports(String fileType);
}