package com.gsy.ai.agent.service;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface ChatMessageService {

    /**
     * 保存用户消息。
     */
    void saveUserMessage(Long userId, String conversationId, String content);

    /**
     * 保存助手最终回答。
     */
    void saveAssistantMessage(Long userId, String conversationId, String content);

    /**
     * 读取最近消息，并转换为Spring AI Message。
     *
     * 返回顺序必须是从旧到新。
     */
    List<Message> listRecentMessages(String conversationId, int maxMessages);
}