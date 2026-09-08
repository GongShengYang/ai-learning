package com.gsy.ai.workflow.enums;

/** Workflow或单个节点的执行状态。 */
public enum WorkflowStatus {
    /** 尚未开始。 */
    PENDING,

    /** 正在执行。 */
    RUNNING,

    /** 执行成功。 */
    SUCCESS,

    /** 执行失败。 */
    FAILED
}
