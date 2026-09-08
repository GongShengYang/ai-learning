package com.gsy.ai.workflow.dto;

import lombok.Data;

/** Workflow问答请求。 */
@Data
public class WorkflowQuestionRequest {
    /** 当前用户ID；正式项目应从登录态获取，当前学习阶段由请求传入。 */
    private Long userId;

    /** 新会话不传；继续已有会话时传入上一次响应的conversationId。 */
    private String conversationId;

    /** 用户当前问题。 */
    private String question;
}