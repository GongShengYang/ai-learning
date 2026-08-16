package com.gsy.ai.structuredoutput.service.impl;

import com.gsy.ai.common.BusinessException;
import com.gsy.ai.structuredoutput.dto.QuestionIntentResult;
import com.gsy.ai.structuredoutput.enums.QuestionIntent;
import com.gsy.ai.structuredoutput.service.EntityStructuredOutputService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * entity简写示例：Spring AI封装格式生成、模型调用结果提取和对象转换。
 */
@Slf4j
@Service
public class EntityStructuredOutputServiceImpl implements EntityStructuredOutputService {
    private static final int MAX_QUESTION_LENGTH = 5000;
    private static final int MAX_REASON_LENGTH = 100;
    private static final String SYSTEM_PROMPT = """
            你是企业知识库的问题分类器，只负责分类，不回答用户问题。

            分类规则：
            1. KNOWLEDGE_SEARCH：查询知识库正文、项目资料、制度、技术记录或解决方案，needTool=true。
            2. DOCUMENT_COUNT：查询知识库文档数量或规模，needTool=true。
            3. GENERAL_CHAT：无需企业知识库即可回答的一般交流或通用知识，needTool=false。
            4. UNSUPPORTED：意图不清、无法处理或不属于以上类型，needTool=false。
            """;

    private final ChatClient chatClient;

    public EntityStructuredOutputServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public QuestionIntentResult classifyQuestion(String question) {
        validateQuestion(question);

        QuestionIntentResult result;
        try {
            result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("请判断下面用户问题的业务意图：\n" + question.trim())
                    .call()
                    // 内部自动创建BeanOutputConverter，执行getFormat()和convert()。
                    .entity(QuestionIntentResult.class);
        } catch (Exception e) {
            log.error("Structured Output entity转换失败", e);
            throw new BusinessException("模型返回内容不符合结构化格式");
        }

        // entity()只负责结构转换，业务规则仍由应用代码校验。
        validateResult(result);
        return result;
    }

    private void validateQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException("问题长度不能超过5000个字符");
        }
    }

    private void validateResult(QuestionIntentResult result) {
        if (result == null || result.getIntent() == null || result.getNeedTool() == null) {
            throw new BusinessException("模型分类结果缺少必要字段");
        }
        if (result.getConfidence() == null || result.getConfidence() < 0 || result.getConfidence() > 1) {
            throw new BusinessException("模型分类结果confidence必须在0到1之间");
        }
        if (!StringUtils.hasText(result.getReason()) || result.getReason().length() > MAX_REASON_LENGTH
                || !result.getReason().matches(".*[\\u4e00-\\u9fff].*")) {
            throw new BusinessException("模型分类结果reason必须包含中文且不能超过100个字符");
        }

        boolean expectedNeedTool = result.getIntent() == QuestionIntent.KNOWLEDGE_SEARCH
                || result.getIntent() == QuestionIntent.DOCUMENT_COUNT;
        if (result.getNeedTool() != expectedNeedTool) {
            throw new BusinessException("模型分类结果中的intent与needTool不一致");
        }
    }
}
