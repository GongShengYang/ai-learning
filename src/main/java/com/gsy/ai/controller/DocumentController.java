package com.gsy.ai.controller;

import com.gsy.ai.common.Result;
import com.gsy.ai.service.DocumentImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai/document")
public class DocumentController {

    @Resource
    private DocumentImportService documentImportService;

    @PostMapping("/import")
    public Result<Map<String, Object>> importDocument(@RequestParam("file") MultipartFile file) {
        try {
            Long docId = documentImportService.importDocument(file);
            Map<String, Object> data = new HashMap<>();
            data.put("documentId", docId);
            data.put("message", "导入成功");
            return Result.success(data);
        } catch (Exception e) {
            log.error("导入失败", e);
            return Result.fail("导入失败: " + e.getMessage());
        }
    }
}