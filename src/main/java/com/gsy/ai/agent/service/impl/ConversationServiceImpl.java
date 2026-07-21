package com.gsy.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsy.ai.agent.entity.AiConversationDO;
import com.gsy.ai.agent.mapper.AiConversationMapper;
import com.gsy.ai.agent.service.ConversationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ConversationServiceImpl implements ConversationService {

    @Resource
    private AiConversationMapper aiConversationMapper;

    @Override
    public String getOrCreateConversation(Long userId, String conversationId, String firstQuestion) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId不能为空");
        }

        if (!StringUtils.hasText(conversationId)) {
            return createConversation(userId, firstQuestion);
        }

        AiConversationDO conversation =
                aiConversationMapper.selectOne(
                        new LambdaQueryWrapper<AiConversationDO>().eq(AiConversationDO::getConversationId, conversationId)
                                .eq(AiConversationDO::getUserId, userId)
                                .eq(AiConversationDO::getStatus, 1)
                                .eq(AiConversationDO::getDeleted, 0)
                                .last("LIMIT 1")
                );

        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }

        return conversation.getConversationId();
    }

    @Override
    public void updateConversationTime(String conversationId) {
        AiConversationDO conversation = aiConversationMapper.selectOne(
                        new LambdaQueryWrapper<AiConversationDO>()
                                .eq(AiConversationDO::getConversationId, conversationId)
                                .eq(AiConversationDO::getDeleted, 0)
                                .last("LIMIT 1"));

        if (conversation == null) {
            return;
        }

        conversation.setUpdateTime(LocalDateTime.now());
        aiConversationMapper.updateById(conversation);
    }

    private String createConversation(Long userId, String firstQuestion) {
        String conversationId = UUID.randomUUID().toString().replace("-", "");

        AiConversationDO conversation = new AiConversationDO();
        conversation.setConversationId(conversationId);
        conversation.setUserId(userId);
        conversation.setTitle(buildTitle(firstQuestion));
        conversation.setStatus(1);
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(LocalDateTime.now());
        conversation.setDeleted(0);

        int insertCount = aiConversationMapper.insert(conversation);

        if (insertCount != 1) {
            throw new IllegalStateException("创建会话失败");
        }

        return conversationId;
    }

    private String buildTitle(String question) {
        if (!StringUtils.hasText(question)) {
            return "新会话";
        }

        String title = question.trim();
        int maxLength = 30;

        if (title.length() <= maxLength) {
            return title;
        }

        return title.substring(0, maxLength) + "...";
    }
}