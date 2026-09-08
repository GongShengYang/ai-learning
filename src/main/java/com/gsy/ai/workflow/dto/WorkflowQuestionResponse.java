package com.gsy.ai.workflow.dto;

import com.gsy.ai.structuredoutput.enums.QuestionIntent;
import com.gsy.ai.workflow.enums.WorkflowNode;
import com.gsy.ai.workflow.enums.WorkflowStatus;
import com.gsy.ai.workflow.model.WorkflowPlan;
import com.gsy.ai.workflow.model.WorkflowPlanStepResult;
import com.gsy.ai.workflow.model.WorkflowStepRecord;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Workflow问答响应。 */
@Data
@AllArgsConstructor
public class WorkflowQuestionResponse {
    /** 本次实际使用的会话ID，新会话时由服务端生成。 */
    private String conversationId;

    /** 模型识别出的业务意图。 */
    private QuestionIntent intent;

    /** 意图识别的置信度，范围0~1。 */
    private Double confidence;

    /** 实际执行的路由分支，低置信度时可能与intent不同。 */
    private QuestionIntent route;

    /** 复合任务的执行计划；普通问题不需要规划，因此为null。 */
    private WorkflowPlan plan;

    /** 复合任务每一步的实际执行结果；普通问题返回空列表。 */
    private List<WorkflowPlanStepResult> planStepResults;

    /** 整个Workflow最终状态。 */
    private WorkflowStatus status;

    /** 失败发生在哪个节点，成功时为null。 */
    private WorkflowNode failedNode;

    /** 安全失败信息，成功时为null。 */
    private String errorMessage;

    /** 按执行顺序返回的节点轨迹。 */
    private List<WorkflowStepRecord> executionTrace;

    /** 最终回答。 */
    private String answer;
}
