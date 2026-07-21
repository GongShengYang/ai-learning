package com.gsy.ai.agent.service;

public interface ConversationService {

    /**
     * conversationId为空时创建新会话；
     * 不为空时校验会话归属。
     */
    String getOrCreateConversation(Long userId, String conversationId, String firstQuestion);

    /**
     * 更新会话最后活跃时间。
     */
    void updateConversationTime(String conversationId);
}