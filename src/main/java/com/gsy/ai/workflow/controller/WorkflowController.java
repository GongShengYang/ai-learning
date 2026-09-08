package com.gsy.ai.workflow.controller;

import com.gsy.ai.common.Result;
import com.gsy.ai.workflow.dto.WorkflowQuestionRequest;
import com.gsy.ai.workflow.dto.WorkflowQuestionResponse;
import com.gsy.ai.workflow.service.WorkflowService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Workflow问答接口，包含确定路由、执行轨迹和Conversation Memory。 */
@RestController
@RequestMapping("/ai/workflow")
public class WorkflowController {
    @Resource
    private WorkflowService workflowService;

    @PostMapping("/question")
    public Result<WorkflowQuestionResponse> question(@RequestBody WorkflowQuestionRequest request) {
        return Result.success(workflowService.answer(
                request == null ? null : request.getUserId(),
                request == null ? null : request.getConversationId(),
                request == null ? null : request.getQuestion()));
    }
}