package com.gsy.ai.workflow.dto;

import com.gsy.ai.workflow.enums.WorkflowNode;
import com.gsy.ai.workflow.enums.WorkflowPlanAction;
import com.gsy.ai.workflow.enums.WorkflowStatus;
import com.gsy.ai.workflow.model.WorkflowSource;
import lombok.AllArgsConstructor;
import lombok.Data;

/** SSE流中发送的一条Workflow实时事件。 */
@Data
@AllArgsConstructor
public class WorkflowStreamEvent {
    /** type：事件类型，前端根据它区分节点、计划步骤、来源和回答片段。 */
    private String type;

    /** 当前Workflow节点，content事件中为null。 */
    private WorkflowNode node;

    /** 计划步骤序号，只有plan-step事件使用。 */
    private Integer stepNumber;

    /** 计划步骤动作，只有plan-step事件使用。 */
    private WorkflowPlanAction action;

    /** 当前执行状态。 */
    private WorkflowStatus status;

    /** 节点的第几次尝试，从1开始。 */
    private Integer attempt;

    /** 模型本次生成的文本片段，只有content事件使用。 */
    private String content;

    /** 检索到的一条知识库来源，只有source事件使用。 */
    private WorkflowSource source;

    /** 便于前端展示的说明。 */
    private String message;

    /** 固定Workflow大节点发生状态变化时创建，例如意图分类节点开始或完成。 */
    public static WorkflowStreamEvent node(WorkflowNode node, WorkflowStatus status,
                                           Integer attempt, String message) {
        return new WorkflowStreamEvent("node", node, null, null, status, attempt, null, null, message);
    }

    /** Planner动态步骤发生状态变化时创建，例如第1步知识库查询完成。 */
    public static WorkflowStreamEvent planStep(Integer stepNumber, WorkflowPlanAction action,
                                               WorkflowStatus status, String message) {
        return new WorkflowStreamEvent("plan-step", null, stepNumber, action, status, null, null, null, message);
    }

    /** Retriever得到一条来源后创建，前端可在模型回答生成前先展示引用资料。 */
    public static WorkflowStreamEvent source(WorkflowSource source) {
        return new WorkflowStreamEvent("source", null, null, null, null, null, null, source, null);
    }

    /** ROUTE_EXECUTION开始或重试时创建，通知前端清除上一次尝试留下的回答片段和来源。 */
    public static WorkflowStreamEvent routeOutputReset() {
        return new WorkflowStreamEvent("route-output-reset", null, null, null, null, null, null, null, null);
    }

    /** 模型每生成一段回答文本时创建，前端把多个content依次拼成完整回答。 */
    public static WorkflowStreamEvent content(String content) {
        return new WorkflowStreamEvent("content", null, null, null, null, null, content, null, null);
    }
}
