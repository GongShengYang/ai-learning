package com.gsy.ai.workflow.model;

import com.gsy.ai.workflow.enums.WorkflowNode;
import com.gsy.ai.workflow.enums.WorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 一条节点执行记录，用于展示Workflow的执行轨迹。 */
@Data
@AllArgsConstructor
public class WorkflowStepRecord {
    /** 执行的是哪个节点。 */
    private WorkflowNode node;

    /** 当前记录是该节点的第几次尝试，从1开始。 */
    private Integer attempt;

    /** 节点本次尝试的最终状态。 */
    private WorkflowStatus status;

    /** 节点本次尝试的耗时，单位为毫秒。 */
    private Long durationMs;

    /** 节点本次尝试的结果说明，不保存底层异常堆栈。 */
    private String message;
}