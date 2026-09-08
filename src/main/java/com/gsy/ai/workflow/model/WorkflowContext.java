package com.gsy.ai.workflow.model;

import com.gsy.ai.structuredoutput.dto.QuestionIntentResult;
import com.gsy.ai.structuredoutput.enums.QuestionIntent;
import com.gsy.ai.workflow.enums.WorkflowNode;
import com.gsy.ai.workflow.enums.WorkflowStatus;
import lombok.Data;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次Workflow执行过程中共享的状态。
 * 每个请求创建一个Context，不同用户、会话和请求之间不会共享该对象。
 */
@Data
public class WorkflowContext {
    /** 当前用户ID，用于校验会话归属和保存消息。 */
    private final Long userId;

    /** 用户当前问题，是本次Workflow的初始输入。 */
    private final String question;

    /** 请求传入或服务端新建后得到的实际会话ID。 */
    private String conversationId;

    /** 会话中最近的历史消息，读取顺序为从旧到新。 */
    private List<Message> historyMessages = List.of();

    /** 分类节点生成的结构化意图结果。 */
    private QuestionIntentResult intentResult;

    /** 路由节点最终决定执行的分支。 */
    private QuestionIntent route;

    /** 执行节点生成的最终回答。 */
    private String answer;

    /** 整个Workflow当前的执行状态。 */
    private WorkflowStatus status = WorkflowStatus.PENDING;

    /** 当前正在执行的节点，失败时保留为失败节点。 */
    private WorkflowNode currentNode;

    /** 返回给调用方的安全失败信息，不包含底层堆栈和敏感数据。 */
    private String errorMessage;

    /** 按实际执行顺序保存节点轨迹。 */
    private final List<WorkflowStepRecord> executionTrace = new ArrayList<>();

    public WorkflowContext(Long userId, String conversationId, String question) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.question = question;
    }
}