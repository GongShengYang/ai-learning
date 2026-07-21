package com.gsy.ai.agent.dto;

import lombok.Data;

@Data
public class AgentChatRequest {

    /**
     * 当前用户ID。
     *
     * 当前学习阶段暂时由请求传入。
     * 正式项目应从登录态或SecurityContext获取。
     */
    private Long userId;

    /**
     * 继续已有会话时传入。
     * 创建新会话时不传。
     */
    private String conversationId;

    /**
     * 用户当前输入。
     */
    private String question;
}