package com.gsy.ai.workflow.service.impl;

import com.gsy.ai.common.BusinessException;
import com.gsy.ai.workflow.enums.WorkflowPlanAction;
import com.gsy.ai.workflow.model.WorkflowPlan;
import com.gsy.ai.workflow.model.WorkflowPlanStep;
import com.gsy.ai.workflow.service.WorkflowPlannerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Planner负责“想清楚要做哪几步”，但不直接执行Java方法。
 * Java在校验计划后再交给Executor执行，避免模型绕过业务边界调用任意能力。
 */
@Slf4j
@Service
public class WorkflowPlannerServiceImpl implements WorkflowPlannerService {
    private static final int MAX_ATTEMPTS = 2;
    private static final int MIN_STEPS = 2;
    private static final int MAX_STEPS = 3;
    private static final int MAX_GOAL_LENGTH = 200;
    private static final int MAX_INSTRUCTION_LENGTH = 500;
    private static final BeanOutputConverter<WorkflowPlan> OUTPUT_CONVERTER =
            new BeanOutputConverter<>(WorkflowPlan.class);
    private static final String SYSTEM_PROMPT = """
            你是企业知识库Agent的任务规划器，只负责拆分任务，不回答问题。

            可用动作：
            1. KNOWLEDGE_SEARCH：检索企业知识库中的制度、项目资料或技术记录。
            2. DOCUMENT_COUNT：查询知识库文档总数。
            3. GENERAL_CHAT：完成不依赖知识库的普通文本任务。

            只生成完成用户目标所必需的步骤，步骤必须可以独立执行，最多3步。
            """;

    private final ChatClient chatClient;

    public WorkflowPlannerServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public WorkflowPlan createPlan(String question) {
        String format = OUTPUT_CONVERTER.getFormat();
        String previousContent = null;
        String previousError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String content = callModel(question, format, previousContent, previousError);
            log.debug("Workflow Planner第{}次模型原始响应：{}", attempt, content);
            try {
                return convertAndValidate(content);
            } catch (BusinessException e) {
                previousContent = content;
                previousError = e.getMessage();
                log.warn("Workflow Planner第{}次计划无效：{}", attempt, previousError);
            }
        }
        throw new BusinessException("模型连续两次未生成有效的执行计划");
    }

    private String callModel(String question, String format, String previousContent, String previousError) {
        String correction = "";
        if (previousError != null) {
            // 第二次把错误原因返回给模型，让它修正具体问题，而不是原样盲目重试。
            correction = """

                    上一次计划未通过校验。
                    错误原因：%s
                    上一次响应：%s
                    请修正后返回完整计划。
                    """.formatted(previousError, previousContent);
        }
        String userPrompt = """
                请把下面的复合问题拆成执行计划：
                %s

                严格按照以下格式返回，不要添加Markdown代码块或额外说明：
                %s%s
                """.formatted(question, format, correction);
        return chatClient.prompt().system(SYSTEM_PROMPT).user(userPrompt).call().content();
    }

    private WorkflowPlan convertAndValidate(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("模型未返回执行计划");
        }
        WorkflowPlan plan;
        try {
            plan = OUTPUT_CONVERTER.convert(content);
        } catch (Exception e) {
            throw new BusinessException("模型返回内容不符合计划格式");
        }
        validatePlan(plan);
        return plan;
    }

    /** 结构化转换成功不等于计划可执行，Java仍要校验数量、顺序、动作和参数。 */
    private void validatePlan(WorkflowPlan plan) {
        if (plan == null || !StringUtils.hasText(plan.getGoal())
                || plan.getGoal().length() > MAX_GOAL_LENGTH) {
            throw new BusinessException("计划goal不能为空且不能超过200个字符");
        }
        List<WorkflowPlanStep> steps = plan.getSteps();
        if (steps == null || steps.size() < MIN_STEPS || steps.size() > MAX_STEPS) {
            throw new BusinessException("计划步骤数量必须在2到3步之间");
        }
        for (int index = 0; index < steps.size(); index++) {
            WorkflowPlanStep step = steps.get(index);
            int expectedStepNumber = index + 1;
            if (step == null || step.getStepNumber() == null
                    || step.getStepNumber() != expectedStepNumber) {
                throw new BusinessException("计划步骤序号必须从1开始连续递增");
            }
            if (step.getAction() == null) {
                throw new BusinessException("计划步骤缺少action");
            }
            if (!StringUtils.hasText(step.getInstruction())
                    || step.getInstruction().length() > MAX_INSTRUCTION_LENGTH) {
                throw new BusinessException("计划步骤instruction不能为空且不能超过500个字符");
            }
        }
        boolean hasToolStep = steps.stream()
                .anyMatch(step -> step.getAction() != WorkflowPlanAction.GENERAL_CHAT);
        if (!hasToolStep) {
            throw new BusinessException("复合任务计划至少需要一个知识库或文档统计步骤");
        }
    }
}
