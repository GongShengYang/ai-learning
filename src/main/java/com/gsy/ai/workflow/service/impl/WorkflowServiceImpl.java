package com.gsy.ai.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gsy.ai.agent.service.ChatMessageService;
import com.gsy.ai.agent.service.ConversationService;
import com.gsy.ai.common.BusinessException;
import com.gsy.ai.document.entity.DocumentDO;
import com.gsy.ai.document.mapper.DocumentMapper;
import com.gsy.ai.document.rag.retriever.RetrieveRequest;
import com.gsy.ai.document.rag.retriever.Retriever;
import com.gsy.ai.document.rag.store.ChunkWithVector;
import com.gsy.ai.structuredoutput.dto.QuestionIntentResult;
import com.gsy.ai.structuredoutput.enums.QuestionIntent;
import com.gsy.ai.structuredoutput.service.StructuredOutputService;
import com.gsy.ai.workflow.dto.WorkflowQuestionResponse;
import com.gsy.ai.workflow.dto.WorkflowStreamEvent;
import com.gsy.ai.workflow.enums.WorkflowNode;
import com.gsy.ai.workflow.enums.WorkflowStatus;
import com.gsy.ai.workflow.exception.WorkflowNodeTimeoutException;
import com.gsy.ai.workflow.model.WorkflowCitationValidationResult;
import com.gsy.ai.workflow.model.WorkflowContext;
import com.gsy.ai.workflow.model.WorkflowPlanStep;
import com.gsy.ai.workflow.model.WorkflowPlanStepResult;
import com.gsy.ai.workflow.model.WorkflowSource;
import com.gsy.ai.workflow.model.WorkflowStepRecord;
import com.gsy.ai.workflow.service.WorkflowPlannerService;
import com.gsy.ai.workflow.service.WorkflowService;
import com.gsy.ai.workflow.service.WorkflowStreamEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性Workflow：模型负责理解问题，Java负责会话、节点顺序、路由、重试和降级。
 */
@Slf4j
@Service
public class WorkflowServiceImpl implements WorkflowService {
    private static final int MAX_QUESTION_LENGTH = 5000;
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_CLASSIFICATION_HISTORY_MESSAGES = 6;
    private static final int MAX_CLASSIFICATION_INPUT_LENGTH = 4800;

    /** 低于该置信度时，不进入业务路由，降级为无法识别。 */
    private static final double CONFIDENCE_THRESHOLD = 0.6;

    /** 业务执行节点最多尝试2次，即首次执行失败后只重试1次。 */
    private static final int MAX_ROUTE_EXECUTION_ATTEMPTS = 2;
    private static final int KNOWLEDGE_TOP_K = 3;
    private static final int SOURCE_PREVIEW_LENGTH = 200;
    private static final Pattern SOURCE_CITATION_PATTERN = Pattern.compile("\\[(S\\d+)]");

    private final StructuredOutputService structuredOutputService;
    private final Retriever retriever;
    private final DocumentMapper documentMapper;
    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final ChatMessageService chatMessageService;
    private final WorkflowPlannerService workflowPlannerService;
    private final ExecutorService workflowNodeExecutor;

    /** 会话创建、历史读取和消息保存节点的最大等待时间。 */
    @Value("${gsy.workflow.timeout.memory-ms:3000}")
    private long memoryTimeoutMs;

    /** 意图分类节点最多等待时间，配置单位为毫秒。 */
    @Value("${gsy.workflow.timeout.intent-classification-ms:30000}")
    private long intentClassificationTimeoutMs;

    /** 复合任务规划节点最多等待时间，配置单位为毫秒。 */
    @Value("${gsy.workflow.timeout.task-planning-ms:30000}")
    private long taskPlanningTimeoutMs;

    /** Java路由节点最多等待时间，配置单位为毫秒。 */
    @Value("${gsy.workflow.timeout.route-decision-ms:1000}")
    private long routeDecisionTimeoutMs;

    /** 业务执行节点最多等待时间，配置单位为毫秒。 */
    @Value("${gsy.workflow.timeout.route-execution-ms:60000}")
    private long routeExecutionTimeoutMs;

    public WorkflowServiceImpl(StructuredOutputService structuredOutputService,
                               Retriever retriever,
                               DocumentMapper documentMapper,
                               ChatClient chatClient,
                               ConversationService conversationService,
                               ChatMessageService chatMessageService,
                               WorkflowPlannerService workflowPlannerService,
                               @Qualifier("workflowNodeExecutor") ExecutorService workflowNodeExecutor) {
        this.structuredOutputService = structuredOutputService;
        this.retriever = retriever;
        this.documentMapper = documentMapper;
        this.chatClient = chatClient;
        this.conversationService = conversationService;
        this.chatMessageService = chatMessageService;
        this.workflowPlannerService = workflowPlannerService;
        this.workflowNodeExecutor = workflowNodeExecutor;
    }

    @Override
    public WorkflowQuestionResponse answer(Long userId, String conversationId, String question) {
        // 原同步接口不向前端推送中间事件，因此传入一个什么都不做的监听器。
        return answerInternal(userId, conversationId, question, WorkflowStreamEventListener.noop(), false);
    }

    @Override
    public WorkflowQuestionResponse answerWithEvents(Long userId, String conversationId, String question,
                                                     WorkflowStreamEventListener eventListener) {
        // 流式接口传入Controller的Lambda；后面每次onEvent都会回调到Controller发送SSE。
        WorkflowStreamEventListener actualListener = eventListener == null
                ? WorkflowStreamEventListener.noop() : eventListener;
        return answerInternal(userId, conversationId, question, actualListener, true);
    }

    /** 同步和流式接口共用完整业务流程，streamAnswer只决定最终回答是否按片段发送。 */
    private WorkflowQuestionResponse answerInternal(Long userId, String conversationId, String question,
                                                    WorkflowStreamEventListener eventListener,
                                                    boolean streamAnswer) {
        validateRequest(userId, question);
        WorkflowContext context = new WorkflowContext(userId, conversationId, question.trim());

        try {
            context.setStatus(WorkflowStatus.RUNNING);

            // 主流程只写“执行哪个节点”。节点对应的实际业务方法统一放在executeNodeAction中。
            // 先读取历史，再保存当前问题，避免当前问题同时出现在history和.user(question)中。
            executeNode(context, WorkflowNode.CONVERSATION_PREPARATION, eventListener, streamAnswer);
            executeNode(context, WorkflowNode.USER_MESSAGE_PERSISTENCE, eventListener, streamAnswer);
            executeNode(context, WorkflowNode.INTENT_CLASSIFICATION, eventListener, streamAnswer);
            executeNode(context, WorkflowNode.ROUTE_DECISION, eventListener, streamAnswer);
            // 普通问题已经有唯一分支，不需要额外调用Planner，避免增加延迟和Token消耗。
            if (context.getRoute() == QuestionIntent.COMPOSITE_TASK) {
                // 复合任务由模型拆成2到3步，再由Java校验动作白名单。
                executeNode(context, WorkflowNode.TASK_PLANNING, eventListener, streamAnswer);
            }
            // 普通任务执行唯一分支；复合任务由Executor依次执行计划中的步骤。
            executeNode(context, WorkflowNode.ROUTE_EXECUTION, eventListener, streamAnswer);
            // 只在回答成功生成后保存助手消息，并更新会话最后活跃时间。
            executeNode(context, WorkflowNode.ASSISTANT_MESSAGE_PERSISTENCE, eventListener, streamAnswer);

            context.setStatus(WorkflowStatus.SUCCESS);
            context.setCurrentNode(null);
        } catch (RuntimeException e) {
            // 最终失败时保留完整服务端日志，并根据失败节点生成可展示的降级回答。
            log.error("Workflow执行失败，conversationId={}，failedNode={}",
                    context.getConversationId(), context.getCurrentNode(), e);
            applyFallback(context);
        }

        QuestionIntentResult intentResult = context.getIntentResult();
        QuestionIntent intent = intentResult == null ? null : intentResult.getIntent();
        Double confidence = intentResult == null ? null : intentResult.getConfidence();
        WorkflowNode failedNode = context.getStatus() == WorkflowStatus.FAILED ? context.getCurrentNode() : null;
        return new WorkflowQuestionResponse(context.getConversationId(), intent, confidence, context.getRoute(),
                context.getPlan(), List.copyOf(context.getPlanStepResults()),
                List.copyOf(context.getSources()), context.getCitationValidation(),
                context.getStatus(), failedNode, context.getErrorMessage(),
                List.copyOf(context.getExecutionTrace()), context.getAnswer());
    }

    /** 创建新会话或校验已有会话归属，然后读取本轮问题之前的最近20条消息。 */
    private void prepareConversation(WorkflowContext context) {
        String actualConversationId = conversationService.getOrCreateConversation(
                context.getUserId(), context.getConversationId(), context.getQuestion());
        context.setConversationId(actualConversationId);

        List<Message> historyMessages = chatMessageService.listRecentMessages(
                actualConversationId, MAX_HISTORY_MESSAGES);
        context.setHistoryMessages(historyMessages == null ? List.of() : List.copyOf(historyMessages));
    }

    /** 在调用模型前保存用户真实提交的问题，模型失败时仍保留这条用户消息。 */
    private void saveUserMessage(WorkflowContext context) {
        chatMessageService.saveUserMessage(
                context.getUserId(), context.getConversationId(), context.getQuestion());
    }

    /** 只在回答成功生成后保存助手消息，并更新会话最后活跃时间。 */
    private void saveAssistantMessage(WorkflowContext context) {
        chatMessageService.saveAssistantMessage(
                context.getUserId(), context.getConversationId(), context.getAnswer());
        conversationService.updateConversationTime(context.getConversationId());
    }

    /**
     * 节点执行模板：统一记录每次尝试、超时和失败信息。
     * 当前ROUTE_EXECUTION都是查询或模型回答，因此普通异常可以重试；写入消息的节点不重试。
     */
    private void executeNode(WorkflowContext context, WorkflowNode node,
                             WorkflowStreamEventListener eventListener, boolean streamAnswer) {
        int maxAttempts = getMaxAttempts(context, node);
        long timeoutMs = getNodeTimeoutMillis(node);
        context.setCurrentNode(node);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            WorkflowStepRecord record = new WorkflowStepRecord(
                    node, attempt, WorkflowStatus.RUNNING, null, "节点执行中");
            context.getExecutionTrace().add(record);

            // 原有逻辑负责把RUNNING记录到Context；新增逻辑把同一个状态实时通知给SSE Controller。
            // node()只是创建事件对象，onEvent()负责调用Controller传入的Lambda，真正发送发生在emitter.send()。
            eventListener.onEvent(WorkflowStreamEvent.node(
                    node, WorkflowStatus.RUNNING, attempt, "节点执行中"));
            long startNanos = System.nanoTime();

            try {
                runWithTimeout(context, node, eventListener, streamAnswer, timeoutMs);
                record.setStatus(WorkflowStatus.SUCCESS);
                record.setDurationMs(elapsedMillis(startNanos));
                record.setMessage("节点执行成功");
                // 节点成功后，在修改服务端轨迹的同时，再向前端推送一条SUCCESS事件。
                eventListener.onEvent(WorkflowStreamEvent.node(
                        node, WorkflowStatus.SUCCESS, attempt, "节点执行成功"));
                return;
            } catch (RuntimeException e) {
                boolean timeout = e instanceof WorkflowNodeTimeoutException;
                // 超时后底层调用不一定立即停止，不能再次启动同一个节点形成并行重复执行。
                boolean retryAllowed = !timeout && !Thread.currentThread().isInterrupted();
                boolean willRetry = retryAllowed && attempt < maxAttempts;
                String errorMessage = timeout ? getSafeTimeoutMessage(node) : getSafeErrorMessage(node);
                record.setStatus(WorkflowStatus.FAILED);
                record.setDurationMs(elapsedMillis(startNanos));
                record.setMessage(willRetry ? errorMessage + "，准备重试" : errorMessage);
                // 失败事件包含attempt和是否准备重试，前端因此能实时展示失败发生在哪一次尝试。
                eventListener.onEvent(WorkflowStreamEvent.node(
                        node, WorkflowStatus.FAILED, attempt, record.getMessage()));

                if (willRetry) {
                    log.warn("Workflow节点执行失败，准备重试，node={}，attempt={}/{}，error={}",
                            node, attempt, maxAttempts, e.getMessage());
                    continue;
                }

                context.setStatus(WorkflowStatus.FAILED);
                context.setErrorMessage(errorMessage);
                throw e;
            }
        }
    }

    /**
     * 明确写出“节点枚举 -> 业务方法”的对应关系。
     * 以前由调用方传入Runnable Lambda，阅读时必须跳到executeNode参数才能知道它会执行什么；
     * 现在只要看这个switch，就能找到每一个Workflow节点真正调用的方法。
     */
    private void executeNodeAction(WorkflowContext context, WorkflowNode node,
                                   WorkflowStreamEventListener eventListener, boolean streamAnswer) {
        switch (node) {
            case CONVERSATION_PREPARATION -> prepareConversation(context);
            case USER_MESSAGE_PERSISTENCE -> saveUserMessage(context);
            case INTENT_CLASSIFICATION -> classifyIntent(context);
            case ROUTE_DECISION -> decideRoute(context);
            case TASK_PLANNING -> createPlan(context);
            case ROUTE_EXECUTION -> executeRoute(context, eventListener, streamAnswer);
            case ASSISTANT_MESSAGE_PERSISTENCE -> saveAssistantMessage(context);
        }
    }

    /**
     * 在线程池中执行节点，并只等待配置的最长时间。
     * cancel(true)只发送中断信号，生产环境仍需配置HTTP、数据库和向量库自身的底层超时。
     */
    private void runWithTimeout(WorkflowContext context, WorkflowNode node,
                                WorkflowStreamEventListener eventListener, boolean streamAnswer,
                                long timeoutMs) {
        // 线程池要求提交Runnable。这里使用有名字的任务类，避免把业务动作藏在Lambda中。
        WorkflowNodeTask nodeTask = new WorkflowNodeTask(context, node, eventListener, streamAnswer);
        Future<?> future = workflowNodeExecutor.submit(nodeTask);
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new WorkflowNodeTimeoutException("Workflow节点执行超过" + timeoutMs + "毫秒");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待Workflow节点执行结果时被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Workflow节点执行异常", cause);
        }
    }

    /** 分类内部已有纠正重试，消息写入不能无脑重试，只有只读业务执行节点最多执行2次。 */
    private int getMaxAttempts(WorkflowContext context, WorkflowNode node) {
        if (node == WorkflowNode.ROUTE_EXECUTION && context.getRoute() != QuestionIntent.UNSUPPORTED) {
            return MAX_ROUTE_EXECUTION_ATTEMPTS;
        }
        return 1;
    }

    /** 根据节点类型选择超时时间，数据库Memory节点统一使用memory-ms。 */
    private long getNodeTimeoutMillis(WorkflowNode node) {
        long timeoutMs = switch (node) {
            case CONVERSATION_PREPARATION, USER_MESSAGE_PERSISTENCE, ASSISTANT_MESSAGE_PERSISTENCE -> memoryTimeoutMs;
            case INTENT_CLASSIFICATION -> intentClassificationTimeoutMs;
            case ROUTE_DECISION -> routeDecisionTimeoutMs;
            case TASK_PLANNING -> taskPlanningTimeoutMs;
            case ROUTE_EXECUTION -> routeExecutionTimeoutMs;
        };
        return Math.max(timeoutMs, 1L);
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** 普通异常对外只暴露节点级安全信息，完整原因保留在服务端日志。 */
    private String getSafeErrorMessage(WorkflowNode node) {
        return switch (node) {
            case CONVERSATION_PREPARATION -> "会话准备失败";
            case USER_MESSAGE_PERSISTENCE -> "用户消息保存失败";
            case INTENT_CLASSIFICATION -> "问题意图识别失败";
            case ROUTE_DECISION -> "问题路由决策失败";
            case TASK_PLANNING -> "复合任务规划失败";
            case ROUTE_EXECUTION -> "问题处理分支执行失败";
            case ASSISTANT_MESSAGE_PERSISTENCE -> "助手消息保存失败";
        };
    }

    /** 超时和普通异常分开描述，方便快速判断失败类型。 */
    private String getSafeTimeoutMessage(WorkflowNode node) {
        return switch (node) {
            case CONVERSATION_PREPARATION -> "会话准备超时";
            case USER_MESSAGE_PERSISTENCE -> "用户消息保存超时";
            case INTENT_CLASSIFICATION -> "问题意图识别超时";
            case ROUTE_DECISION -> "问题路由决策超时";
            case TASK_PLANNING -> "复合任务规划超时";
            case ROUTE_EXECUTION -> "问题处理分支执行超时";
            case ASSISTANT_MESSAGE_PERSISTENCE -> "助手消息保存超时";
        };
    }

    /** 根据最终失败位置生成降级回答，不再调用外部服务。 */
    private void applyFallback(WorkflowContext context) {
        WorkflowNode failedNode = context.getCurrentNode();
        if (failedNode == null) {
            context.setAnswer("抱歉，本次问题处理失败，请稍后重试。");
            return;
        }

        String fallbackAnswer = switch (failedNode) {
            case CONVERSATION_PREPARATION -> "会话服务暂时不可用，请稍后重试。";
            case USER_MESSAGE_PERSISTENCE -> "你的问题暂时无法保存，请稍后重试。";
            case INTENT_CLASSIFICATION -> "暂时无法识别你的问题，请稍后重试或补充更明确的描述。";
            case ROUTE_DECISION -> "问题已经识别，但暂时无法选择处理方式，请稍后重试。";
            case TASK_PLANNING -> "问题包含多个任务，但暂时无法生成执行计划，请稍后重试。";
            case ROUTE_EXECUTION -> getRouteFallback(context.getRoute());
            // 回答已经生成但保存失败时，保留原回答，通过status和errorMessage告知保存异常。
            case ASSISTANT_MESSAGE_PERSISTENCE -> StringUtils.hasText(context.getAnswer())
                    ? context.getAnswer()
                    : "回答已经生成，但会话记录保存失败。";
        };
        context.setAnswer(fallbackAnswer);
    }

    private String getRouteFallback(QuestionIntent route) {
        if (route == null) {
            return "抱歉，本次问题处理失败，请稍后重试。";
        }
        return switch (route) {
            case KNOWLEDGE_SEARCH -> "知识库查询暂时不可用，请稍后重试。";
            case DOCUMENT_COUNT -> "文档统计服务暂时不可用，请稍后重试。";
            case COMPOSITE_TASK -> "复合任务执行暂时不可用，请稍后重试。";
            case GENERAL_CHAT -> "模型问答服务暂时不可用，请稍后重试。";
            case UNSUPPORTED -> "抱歉，我暂时无法确认你的意图，请补充更明确的描述。";
        };
    }

    /** 结合最近对话判断当前问题的意图，使“那它有多少个？”这类追问也能被理解。 */
    private void classifyIntent(WorkflowContext context) {
        String classificationQuestion = buildClassificationQuestion(context);
        QuestionIntentResult intentResult = structuredOutputService.classifyQuestion(classificationQuestion);
        context.setIntentResult(intentResult);
    }

    /** Planner读取当前问题和少量最近历史，把复合目标拆成Java可以校验、执行的结构化步骤。 */
    private void createPlan(WorkflowContext context) {
        String planningQuestion = buildClassificationQuestion(context);
        context.setPlan(workflowPlannerService.createPlan(planningQuestion));
    }

    /**
     * 分类只携带最近6条历史，并限制总长度，避免把20条历史全部塞给分类模型浪费Token。
     * 从最新消息向前选择，再恢复成从旧到新的顺序。
     */
    private String buildClassificationQuestion(WorkflowContext context) {
        List<Message> historyMessages = context.getHistoryMessages();
        if (historyMessages.isEmpty()) {
            return context.getQuestion();
        }

        int remainingLength = Math.max(
                MAX_CLASSIFICATION_INPUT_LENGTH - context.getQuestion().length() - 150, 0);
        List<String> selectedHistory = new ArrayList<>();
        int selectedCount = 0;

        for (int i = historyMessages.size() - 1;
             i >= 0 && selectedCount < MAX_CLASSIFICATION_HISTORY_MESSAGES && remainingLength > 0;
             i--) {
            Message message = historyMessages.get(i);
            if (!StringUtils.hasText(message.getText())) {
                continue;
            }
            String line = message.getMessageType() + "：" + message.getText().trim() + System.lineSeparator();
            if (line.length() > remainingLength) {
                line = line.substring(0, remainingLength);
            }
            selectedHistory.add(0, line);
            remainingLength -= line.length();
            selectedCount++;
        }

        if (selectedHistory.isEmpty()) {
            return context.getQuestion();
        }
        return """
                请结合历史对话，只判断当前用户问题的业务意图。
                【历史对话】
                %s
                【当前用户问题】
                %s
                """.formatted(String.join("", selectedHistory), context.getQuestion());
    }

    /** 根据意图和置信度，由Java确定唯一执行分支。 */
    private void decideRoute(WorkflowContext context) {
        QuestionIntentResult intentResult = context.getIntentResult();
        Double confidence = intentResult.getConfidence();
        QuestionIntent route = confidence != null && confidence >= CONFIDENCE_THRESHOLD
                ? intentResult.getIntent()
                : QuestionIntent.UNSUPPORTED;
        context.setRoute(route);
    }

    /** 只执行Java路由选中的分支；流式接口只影响答案传输方式，不改变业务路由。 */
    private void executeRoute(WorkflowContext context, WorkflowStreamEventListener eventListener,
                              boolean streamAnswer) {
        // 如果上一次是引用校验失败，本次Prompt会携带具体原因，让模型有目标地纠正。
        WorkflowCitationValidationResult previousValidation = context.getCitationValidation();
        context.setCitationCorrectionHint(previousValidation != null && !previousValidation.isPassed()
                ? previousValidation.getMessage() : null);
        context.setCitationValidation(null);
        // ROUTE_EXECUTION可能整体重试。必须清空上一次检索留下的来源，避免出现重复或失效的S编号。
        context.getSources().clear();
        eventListener.onEvent(WorkflowStreamEvent.routeOutputReset());
        String answer = switch (context.getRoute()) {
            case KNOWLEDGE_SEARCH -> answerByKnowledge(
                    context, context.getQuestion(), null, eventListener, streamAnswer);
            case DOCUMENT_COUNT -> publishWholeAnswer(
                    answerByDocumentCount(), eventListener, streamAnswer);
            case COMPOSITE_TASK -> answerByCompositeTask(context, eventListener, streamAnswer);
            case GENERAL_CHAT -> answerByGeneralChat(
                    context, context.getQuestion(), eventListener, streamAnswer);
            case UNSUPPORTED -> publishWholeAnswer(
                    "抱歉，我暂时无法确认你的意图，请补充更明确的描述。",
                    eventListener, streamAnswer);
        };
        // 校验仍位于ROUTE_EXECUTION内部，所以失败会复用该节点已有的最多2次重试机制。
        validateAnswerCitations(context, answer);
        context.setCitationCorrectionHint(null);
        context.setAnswer(answer);
    }

    /**
     * Executor不让模型直接调用任意方法，而是按计划顺序把action映射到受控Java分支。
     * 整个ROUTE_EXECUTION重试时先清空上次的部分结果，避免响应中出现重复步骤。
     */
    private String answerByCompositeTask(WorkflowContext context,
                                         WorkflowStreamEventListener eventListener,
                                         boolean streamAnswer) {
        if (context.getPlan() == null || context.getPlan().getSteps() == null) {
            throw new BusinessException("复合任务缺少执行计划");
        }
        context.getPlanStepResults().clear();

        for (WorkflowPlanStep step : context.getPlan().getSteps()) {
            WorkflowPlanStepResult stepResult = new WorkflowPlanStepResult(
                    step.getStepNumber(), step.getAction(), step.getInstruction(),
                    WorkflowStatus.RUNNING, null, null);
            context.getPlanStepResults().add(stepResult);
            eventListener.onEvent(WorkflowStreamEvent.planStep(
                    step.getStepNumber(), step.getAction(), WorkflowStatus.RUNNING, "计划步骤执行中"));
            try {
                String result = switch (step.getAction()) {
                    // 中间答案不按Token推送，但知识检索仍使用真实监听器，把source事件及时发给前端。
                    case KNOWLEDGE_SEARCH -> answerByKnowledge(
                            context, step.getInstruction(), step.getStepNumber(), eventListener, false);
                    case DOCUMENT_COUNT -> answerByDocumentCount();
                    case GENERAL_CHAT -> answerByGeneralChat(
                            context, step.getInstruction(), WorkflowStreamEventListener.noop(), false);
                };
                stepResult.setResult(result);
                stepResult.setStatus(WorkflowStatus.SUCCESS);
                eventListener.onEvent(WorkflowStreamEvent.planStep(
                        step.getStepNumber(), step.getAction(), WorkflowStatus.SUCCESS, "计划步骤执行成功"));
            } catch (RuntimeException e) {
                stepResult.setStatus(WorkflowStatus.FAILED);
                stepResult.setErrorMessage("计划第" + step.getStepNumber() + "步执行失败");
                eventListener.onEvent(WorkflowStreamEvent.planStep(
                        step.getStepNumber(), step.getAction(), WorkflowStatus.FAILED,
                        stepResult.getErrorMessage()));
                throw e;
            }
        }
        return composePlanAnswer(context, eventListener, streamAnswer);
    }

    /** 所有步骤完成后由模型统一组织语言，避免把多个孤立结果直接拼接给用户。 */
    private String composePlanAnswer(WorkflowContext context,
                                     WorkflowStreamEventListener eventListener,
                                     boolean streamAnswer) {
        StringBuilder resultText = new StringBuilder();
        for (WorkflowPlanStepResult stepResult : context.getPlanStepResults()) {
            resultText.append("步骤").append(stepResult.getStepNumber())
                    .append("（").append(stepResult.getInstruction()).append("）结果：")
                    .append(stepResult.getResult()).append(System.lineSeparator());
        }
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .system("""
                        你是企业智能助手。请严格依据各步骤结果回答，不得编造，
                        不要向用户暴露Planner、Executor等内部术语。
                        步骤结果中的[S1]、[S2]等是可信来源编号，必须原样保留，禁止创造不存在的编号。
                        %s
                        """.formatted(buildCitationCorrection(context)))
                .user("""
                        用户原始问题：%s

                        已完成的步骤结果：
                        %s

                        请整合为一段完整、清晰的最终回答，并确保用户提出的每个子任务都有对应答案。
                        """.formatted(context.getQuestion(), resultText));
        String answer = callOrStream(requestSpec, eventListener, streamAnswer);
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException("复合任务汇总回答生成失败");
        }
        return answer;
    }

    /** 复合任务执行时使用子步骤instruction检索，避免每一步都拿原始复合问题查询。 */
    private String answerByKnowledge(WorkflowContext context, String question, Integer planStepNumber,
                                     WorkflowStreamEventListener eventListener,
                                     boolean streamAnswer) {
        RetrieveRequest request = new RetrieveRequest();
        request.setQuestion(question);
        request.setDocumentId(null);
        request.setTopK(KNOWLEDGE_TOP_K);

        List<ChunkWithVector> chunks;
        try {
            chunks = retriever.retrieve(request);
        } catch (IOException e) {
            throw new BusinessException("知识库检索失败：" + e.getMessage());
        }
        if (chunks == null || chunks.isEmpty()) {
            return "知识库中没有找到相关信息。";
        }

        // 先由Java建立“检索切片 -> S编号”的稳定映射，再把同一映射交给模型和前端。
        List<WorkflowSource> currentSources = recordSources(
                context, chunks, planStepNumber, eventListener);

        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .system("""
                        你是企业知识库助手。
                        仅根据提供的资料回答，不得编造资料中没有的内容。
                        引用具体事实时，必须在对应句末标注来源编号，例如[S1]。
                        只能使用参考资料中存在的来源编号，禁止创造不存在的编号。
                        历史对话只用于理解当前问题，无关内容不要强行关联。
                        如果资料不足以回答，请明确说明。
                        %s
                        """.formatted(buildCitationCorrection(context)));
        addHistory(requestSpec, context.getHistoryMessages());

        requestSpec = requestSpec.user("""
                        参考资料：
                        %s

                        当前用户问题：%s
                        """.formatted(buildContext(chunks, currentSources), question));
        String answer = callOrStream(requestSpec, eventListener, streamAnswer);
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException("知识库回答生成失败");
        }
        return answer;
    }

    /**
     * 把Retriever结果登记成可追踪来源。
     * sourceId从Context当前数量继续递增，因此复合任务多个知识检索步骤也不会重复编号。
     */
    private List<WorkflowSource> recordSources(WorkflowContext context, List<ChunkWithVector> chunks,
                                               Integer planStepNumber,
                                               WorkflowStreamEventListener eventListener) {
        List<WorkflowSource> currentSources = new ArrayList<>();
        for (ChunkWithVector chunk : chunks) {
            String sourceId = "S" + (context.getSources().size() + 1);
            WorkflowSource source = new WorkflowSource(sourceId, planStepNumber,
                    chunk.getDocumentId(), chunk.getChunkIndex(), chunk.getScore(),
                    buildContentPreview(chunk.getContent()));
            context.getSources().add(source);
            currentSources.add(source);
            // 流式接口会立即展示来源；同步接口传入noop监听器，不会产生额外操作。
            eventListener.onEvent(WorkflowStreamEvent.source(source));
        }
        return currentSources;
    }

    /** Prompt中的资料和currentSources按同一下标对应，确保模型看到的S编号与接口返回一致。 */
    private String buildContext(List<ChunkWithVector> chunks, List<WorkflowSource> currentSources) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkWithVector chunk = chunks.get(i);
            WorkflowSource source = currentSources.get(i);
            sb.append("【").append(source.getSourceId())
              .append("，documentId=").append(chunk.getDocumentId())
              .append(", chunkIndex=").append(chunk.getChunkIndex())
              .append(", similarity=").append(chunk.getScore()).append("】")
              .append(System.lineSeparator());
            sb.append(chunk.getContent()).append(System.lineSeparator()).append(System.lineSeparator());
        }
        return sb.toString();
    }

    /** 对外只返回单行摘要；完整切片仍只在服务端Prompt内部使用。 */
    private String buildContentPreview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String preview = content.trim().replaceAll("\\s+", " ");
        return preview.length() <= SOURCE_PREVIEW_LENGTH
                ? preview : preview.substring(0, SOURCE_PREVIEW_LENGTH) + "……";
    }

    /**
     * 校验最终用户可见答案中的[S编号]：
     * 1. 有检索资料时至少引用一个来源；
     * 2. 每个引用编号必须存在于本次Retriever结果中。
     * 校验失败直接抛异常，由外层ROUTE_EXECUTION决定是否重试。
     */
    private void validateAnswerCitations(WorkflowContext context, String answer) {
        if (context.getRoute() != QuestionIntent.KNOWLEDGE_SEARCH
                && context.getRoute() != QuestionIntent.COMPOSITE_TASK) {
            return;
        }
        if (context.getSources().isEmpty()) {
            context.setCitationValidation(new WorkflowCitationValidationResult(
                    false, true, List.of(), List.of(), "本次未检索到资料，无需来源引用"));
            return;
        }

        Set<String> availableSourceIds = new LinkedHashSet<>();
        for (WorkflowSource source : context.getSources()) {
            availableSourceIds.add(source.getSourceId());
        }

        // LinkedHashSet既能去重，也能保留编号在回答中的首次出现顺序。
        Set<String> citedSourceIds = new LinkedHashSet<>();
        Matcher matcher = SOURCE_CITATION_PATTERN.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            citedSourceIds.add(matcher.group(1));
        }

        // 使用普通循环列出无效编号，更便于调试时逐行观察availableSourceIds和sourceId。
        List<String> invalidSourceIds = new ArrayList<>();
        for (String sourceId : citedSourceIds) {
            if (!availableSourceIds.contains(sourceId)) {
                invalidSourceIds.add(sourceId);
            }
        }
        if (citedSourceIds.isEmpty()) {
            failCitationValidation(context, List.of(), List.of(),
                    "回答使用了知识库资料，但没有标注任何[S编号]来源");
        }
        if (!invalidSourceIds.isEmpty()) {
            failCitationValidation(context, List.copyOf(citedSourceIds), invalidSourceIds,
                    "回答引用了本次检索中不存在的来源编号：" + String.join("、", invalidSourceIds));
        }

        context.setCitationValidation(new WorkflowCitationValidationResult(
                true, true, List.copyOf(citedSourceIds), List.of(), "引用编号校验通过"));
    }

    /** 先把失败详情写入Context，再抛异常，保证重试Prompt和最终错误响应都能看到原因。 */
    private void failCitationValidation(WorkflowContext context, List<String> citedSourceIds,
                                        List<String> invalidSourceIds, String message) {
        context.setCitationValidation(new WorkflowCitationValidationResult(
                true, false, citedSourceIds, invalidSourceIds, message));
        throw new BusinessException(message);
    }

    /** 第二次生成时增加纠错要求；第一次执行返回空串，不改变正常Prompt。 */
    private String buildCitationCorrection(WorkflowContext context) {
        if (!StringUtils.hasText(context.getCitationCorrectionHint())) {
            return "";
        }
        return "上一次回答未通过引用校验：" + context.getCitationCorrectionHint()
                + "。请重新回答并严格修正该问题。";
    }

    private String answerByDocumentCount() {
        Long count = documentMapper.selectCount(new QueryWrapper<DocumentDO>());
        return "当前知识库共有" + count + "篇文档。";
    }

    /** 复合任务执行时只处理当前计划步骤，不把整个复合问题重复交给模型。 */
    private String answerByGeneralChat(WorkflowContext context, String question,
                                       WorkflowStreamEventListener eventListener,
                                       boolean streamAnswer) {
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .system("你是企业智能助手，请结合相关历史对话，简洁准确地回答当前问题。");
        addHistory(requestSpec, context.getHistoryMessages());

        requestSpec = requestSpec.user(question);
        String answer = callOrStream(requestSpec, eventListener, streamAnswer);
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException("普通问答生成失败");
        }
        return answer;
    }

    /** 同步接口一次性取完整回答；流式接口逐段收集并发送content事件。 */
    private String callOrStream(ChatClient.ChatClientRequestSpec requestSpec,
                                WorkflowStreamEventListener eventListener,
                                boolean streamAnswer) {
        if (!streamAnswer) {
            return requestSpec.call().content();
        }
        Flux<String> contentFlux = requestSpec.stream().content();
        StringBuilder fullAnswer = new StringBuilder();
        // toIterable会等待并逐个取得模型返回的文本片段，效果等同于原来的doOnNext(...).blockLast()。
        // 普通for循环可以直接看出每收到一个片段，就会先拼接完整回答，再通知SSE前端。
        for (String content : contentFlux.toIterable()) {
            if (StringUtils.hasLength(content)) {
                fullAnswer.append(content);
                eventListener.onEvent(WorkflowStreamEvent.content(content));
            }
        }
        return fullAnswer.toString();
    }

    /** 数据库统计和降级文本没有Token流，作为一个完整content事件发送。 */
    private String publishWholeAnswer(String answer, WorkflowStreamEventListener eventListener,
                                      boolean streamAnswer) {
        if (streamAnswer && StringUtils.hasLength(answer)) {
            eventListener.onEvent(WorkflowStreamEvent.content(answer));
        }
        return answer;
    }

    /** 历史为空时不调用messages()，避免向ChatClient加入无意义的空集合。 */
    private void addHistory(ChatClient.ChatClientRequestSpec requestSpec, List<Message> historyMessages) {
        if (historyMessages != null && !historyMessages.isEmpty()) {
            requestSpec.messages(historyMessages);
        }
    }

    private void validateRequest(Long userId, String question) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId不能为空");
        }
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException("问题长度不能超过5000个字符");
        }
    }

    /**
     * 一个节点在线程池中的实际任务。
     * 它只负责保存执行节点所需的参数，run()被线程池调用后，再进入上面的executeNodeAction。
     */
    private final class WorkflowNodeTask implements Runnable {
        private final WorkflowContext context;
        private final WorkflowNode node;
        private final WorkflowStreamEventListener eventListener;
        private final boolean streamAnswer;

        private WorkflowNodeTask(WorkflowContext context, WorkflowNode node,
                                 WorkflowStreamEventListener eventListener, boolean streamAnswer) {
            this.context = context;
            this.node = node;
            this.eventListener = eventListener;
            this.streamAnswer = streamAnswer;
        }

        @Override
        public void run() {
            executeNodeAction(context, node, eventListener, streamAnswer);
        }
    }
}
