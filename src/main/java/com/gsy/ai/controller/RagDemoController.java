package com.gsy.ai.controller;

import com.gsy.ai.common.Result;
import com.gsy.ai.dto.RagQuestionRequest;
import com.gsy.ai.service.RagDemoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/ai/demo")
public class RagDemoController {

    @Resource
    private RagDemoService ragDemoService;


    @PostMapping("/ragDemo")
    public Result<String> ragDemo(@RequestBody RagQuestionRequest ragQuestionRequest) {
        // Controller 只做基础校验和调用 Service
        if (ragQuestionRequest.getQuestion() == null || ragQuestionRequest.getQuestion().trim().isEmpty()) {
            return Result.fail("问题不能为空");
        }

        try {
            String answer = ragDemoService.getRagDemo(ragQuestionRequest);
            return Result.success(answer);
        } catch (Exception e) {
            log.error("RAG问答异常", e);
            // 这里会被 GlobalExceptionHandler 统一处理，但为了保持兼容，也返回 Result
            return Result.fail("问答失败: " + e.getMessage());
        }
    }
}