package com.gsy.ai.rag.parser;

import com.gsy.ai.common.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PdfParser implements DocumentParser {

    @Override
    public String parse(MultipartFile file) throws Exception {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.trim().isEmpty()) {
                throw new BusinessException("该PDF为扫描版或图片版，暂不支持OCR，请上传文本型PDF");
            }
            return text;
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }
}