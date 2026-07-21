package com.gsy.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsy.ai.agent.entity.AiChatMessageDO;
import com.gsy.ai.agent.enums.ChatMessageRole;
import com.gsy.ai.agent.mapper.AiChatMessageMapper;
import com.gsy.ai.agent.service.ChatMessageService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    @Resource
    private AiChatMessageMapper aiChatMessageMapper;

    @Override
    public void saveUserMessage(Long userId, String conversationId, String content) {
        saveMessage(userId, conversationId, ChatMessageRole.USER.getCode(), content);
    }

    @Override
    public void saveAssistantMessage(Long userId, String conversationId, String content) {
        saveMessage(userId, conversationId, ChatMessageRole.ASSISTANT.getCode(), content);
    }

    @Override
    public List<Message> listRecentMessages(String conversationId, int maxMessages) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId不能为空");
        }

        if (maxMessages <= 0) {
            return Collections.emptyList();
        }

        /*
         * 第一步：按ID倒序，取最近N条。
         *
         * 数据库返回：
         * 最新消息
         * 次新消息
         * 更早消息
         */
        List<AiChatMessageDO> records = aiChatMessageMapper.selectList(
                        new LambdaQueryWrapper<AiChatMessageDO>()
                                .eq(AiChatMessageDO::getConversationId, conversationId)
                                .eq(AiChatMessageDO::getDeleted, 0)
                                .orderByDesc(AiChatMessageDO::getId)
                                .last("LIMIT " + maxMessages)
                );

        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        /*
         * 第二步：反转顺序。
         *
         * 模型接收的历史必须是：
         * 最早消息
         * 较新消息
         * 最新消息
         */
        Collections.reverse(records);

        List<Message> messages = new ArrayList<>(records.size());

        for (AiChatMessageDO record : records) {
            Message message = convertToSpringAiMessage(record);

            if (message != null) {
                messages.add(message);
            }
        }

        return messages;
    }

    private void saveMessage(Long userId, String conversationId, String role, String content) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId不能为空");
        }

        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId不能为空");
        }

        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("消息角色不能为空");
        }

        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        AiChatMessageDO message = new AiChatMessageDO();
        message.setUserId(userId);
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        message.setDeleted(0);

        int insertCount = aiChatMessageMapper.insert(message);

        if (insertCount != 1) {
            throw new IllegalStateException("保存聊天消息失败");
        }
    }

    /**
     * 将数据库消息转换为Spring AI Message。
     */
    private Message convertToSpringAiMessage(AiChatMessageDO record) {
        if (record == null || !StringUtils.hasText(record.getRole()) || !StringUtils.hasText(record.getContent())) {
            return null;
        }

        String role = record.getRole();
        String content = record.getContent();

        if (ChatMessageRole.USER.getCode().equals(role)) {
            return new UserMessage(content);
        }

        if (ChatMessageRole.ASSISTANT.getCode().equals(role)) {
            return new AssistantMessage(content);
        }

        if (ChatMessageRole.SYSTEM.getCode().equals(role)) {
            return new SystemMessage(content);
        }

        /*
         * 数据库出现未知角色时不直接构造消息，
         * 避免把异常数据送入模型。
         */
        return null;
    }
}