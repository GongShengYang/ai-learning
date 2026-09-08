package com.gsy.ai.structuredoutput.service.impl;

import com.gsy.ai.common.BusinessException;
import com.gsy.ai.structuredoutput.dto.QuestionIntentResult;
import com.gsy.ai.structuredoutput.enums.QuestionIntent;
import com.gsy.ai.structuredoutput.service.StructuredOutputService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 显式演示 getFormat -> content -> convert -> validate 的完整链路。
 */
@Slf4j
@Service
public class StructuredOutputServiceImpl implements StructuredOutputService {
    private static final int MAX_QUESTION_LENGTH = 5000;
    private static final int MAX_REASON_LENGTH = 100;
    private static final int MAX_ATTEMPTS = 2;
    private static final BeanOutputConverter<QuestionIntentResult> OUTPUT_CONVERTER =
            new BeanOutputConverter<>(QuestionIntentResult.class);
    private static final String SYSTEM_PROMPT = """
            你是企业知识库的问题分类器，只负责分类，不回答用户问题。

            分类规则：
            1. KNOWLEDGE_SEARCH：查询知识库正文、项目资料、制度、技术记录或解决方案，needTool=true。
            2. DOCUMENT_COUNT：查询知识库文档数量或规模，needTool=true。
            3. COMPOSITE_TASK：同一个问题包含两个或以上子任务，且至少一个需要知识库或文档统计，needTool=true。
            4. GENERAL_CHAT：无需企业知识库即可回答的一般交流或通用知识，needTool=false。
            5. UNSUPPORTED：意图不清、无法处理或不属于以上类型，needTool=false。
            """;

    private final ChatClient chatClient;

    public StructuredOutputServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public QuestionIntentResult classifyQuestion(String question) {
        validateQuestion(question);

        // getFormat() 读取结果类的字段、类型、枚举和@JsonPropertyDescription，生成格式说明。
        // 它只负责“生成说明”，下面仍需要由我们把format手动放入Prompt，AI才能看到。
        String format = OUTPUT_CONVERTER.getFormat();
        String previousContent = null;
        String previousError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String modelContent = callModel(question.trim(), format, previousContent, previousError);
            log.debug("Structured Output 第{}次模型原始响应: {}", attempt, modelContent);
            try {
                return convertAndValidate(modelContent);
            } catch (BusinessException e) {
                log.warn("Structured Output 第{}次结果无效: {}", attempt, e.getMessage());
                previousContent = modelContent;
                previousError = e.getMessage();
            }
        }
        throw new BusinessException("模型连续两次未返回有效的结构化分类结果");
    }

    private String callModel(String question, String format, String previousContent, String previousError) {
        String correction = "";
        if (previousError != null) {
            // 第二次请求携带失败原因和上次响应，让模型有针对性地纠正，而不是盲目重试。
            correction = """

                    上一次返回未通过校验。
                    错误原因：%s
                    上一次响应：%s
                    请修正后重新返回完整JSON。
                    """.formatted(previousError, previousContent);
        }
        String userPrompt = """
                请判断下面用户问题的业务意图：
                %s

                请严格按照以下格式返回，不要添加Markdown代码块或额外说明：
                %s%s
                """.formatted(question, format, correction);
        return chatClient.prompt().system(SYSTEM_PROMPT).user(userPrompt).call().content();
    }

    private QuestionIntentResult convertAndValidate(String modelContent) {
        if (!StringUtils.hasText(modelContent)) {
            throw new BusinessException("模型未返回分类结果");
        }
        QuestionIntentResult result;
        try {
            // convert() 只负责把JSON文本转换成QuestionIntentResult。
            result = OUTPUT_CONVERTER.convert(modelContent);
        } catch (Exception e) {
            throw new BusinessException("模型返回内容不符合结构化格式");
        }
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
        if (result == null) {
            throw new BusinessException("模型分类结果不能为空");
        }
        if (result.getIntent() == null) {
            throw new BusinessException("模型分类结果缺少intent");
        }
        if (result.getNeedTool() == null) {
            throw new BusinessException("模型分类结果缺少needTool");
        }
        if (result.getConfidence() == null || result.getConfidence() < 0 || result.getConfidence() > 1) {
            throw new BusinessException("模型分类结果confidence必须在0到1之间");
        }
        if (!StringUtils.hasText(result.getReason()) || result.getReason().length() > MAX_REASON_LENGTH
                || !result.getReason().matches(".*[\\u4e00-\\u9fff].*")) {
            throw new BusinessException("模型分类结果reason必须包含中文且不能超过100个字符");
        }

        // 搜索和统计必须使用工具；普通聊天和不支持类型不能使用工具。
        boolean expectedNeedTool = result.getIntent() == QuestionIntent.KNOWLEDGE_SEARCH
                || result.getIntent() == QuestionIntent.DOCUMENT_COUNT
                || result.getIntent() == QuestionIntent.COMPOSITE_TASK;
        if (result.getNeedTool() != expectedNeedTool) {
            throw new BusinessException("模型分类结果中的intent与needTool不一致");
        }
    }
}
