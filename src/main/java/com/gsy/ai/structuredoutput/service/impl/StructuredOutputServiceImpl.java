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
    private static final BeanOutputConverter<QuestionIntentResult> OUTPUT_CONVERTER =
            new BeanOutputConverter<>(QuestionIntentResult.class);

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
        String userPrompt = """
                请判断下面用户问题的业务意图：
                %s

                请严格按照以下格式返回，不要添加 Markdown 代码块或额外说明：
                %s
                """.formatted(question.trim(), format);

        String modelContent = chatClient.prompt()
                .system("""
                        你是企业知识库的问题分类器，只负责分类，不回答用户问题。

                        分类规则：
                        1. KNOWLEDGE_SEARCH：查询知识库正文、项目资料、制度、技术记录或解决方案，needTool=true。
                        2. DOCUMENT_COUNT：查询知识库文档数量或规模，needTool=true。
                        3. GENERAL_CHAT：无需企业知识库即可回答的一般交流或通用知识，needTool=false。
                        4. UNSUPPORTED：意图不清、无法处理或不属于以上类型，needTool=false。

                        confidence 必须在 0 到 1 之间；reason 必须使用中文且不超过 100 个字符。
                        """)
                .user(userPrompt)
                .call()
                .content();

        if (!StringUtils.hasText(modelContent)) {
            throw new BusinessException("模型未返回分类结果");
        }
        log.debug("Structured Output 模型原始响应: {}", modelContent);

        QuestionIntentResult result;
        try {
            // convert() 将模型返回的 JSON 文本转换成 QuestionIntentResult。
            result = OUTPUT_CONVERTER.convert(modelContent);
        } catch (Exception e) {
            log.error("Structured Output 转换失败，模型原始响应: {}", modelContent, e);
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
                || result.getIntent() == QuestionIntent.DOCUMENT_COUNT;
        if (result.getNeedTool() != expectedNeedTool) {
            throw new BusinessException("模型分类结果中的intent与needTool不一致");
        }
    }
}
