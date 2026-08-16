package com.gsy.ai.structuredoutput.controller;

import com.gsy.ai.common.Result;
import com.gsy.ai.structuredoutput.dto.QuestionIntentRequest;
import com.gsy.ai.structuredoutput.dto.QuestionIntentResult;
import com.gsy.ai.structuredoutput.service.EntityStructuredOutputService;
import com.gsy.ai.structuredoutput.service.StructuredOutputService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 独立演示 Structured Output，不接入现有 Agent。
 */
@RestController
@RequestMapping("/ai/structured-output")
public class StructuredOutputController {
    @Resource
    private StructuredOutputService structuredOutputService;

    @Resource
    private EntityStructuredOutputService entityStructuredOutputService;

    @PostMapping("/classify")
    public Result<QuestionIntentResult> classify(@RequestBody QuestionIntentRequest request) {
        return Result.success(structuredOutputService.classifyQuestion(request == null ? null : request.getQuestion()));
    }

    /**
     * 使用entity()简写完成相同分类，便于和/classify的显式写法对比。
     */
    @PostMapping("/classify/entity")
    public Result<QuestionIntentResult> classifyWithEntity(@RequestBody QuestionIntentRequest request) {
        return Result.success(entityStructuredOutputService.classifyQuestion(
                request == null ? null : request.getQuestion()));
    }
}
