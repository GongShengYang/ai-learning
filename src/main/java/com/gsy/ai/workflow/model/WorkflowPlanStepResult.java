package com.gsy.ai.workflow.model;

import com.gsy.ai.workflow.enums.WorkflowPlanAction;
import com.gsy.ai.workflow.enums.WorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Executor执行一条计划步骤后产生的结果。 */
@Data
@AllArgsConstructor
public class WorkflowPlanStepResult {
    /** 对应计划中的步骤序号。 */
    private Integer stepNumber;

    /** 实际执行的受控动作。 */
    private WorkflowPlanAction action;

    /** 该步骤的任务描述。 */
    private String instruction;

    /** 该步骤的执行状态；失败时可以直接定位是哪一步。 */
    private WorkflowStatus status;

    /** 该步骤成功后得到的文本结果，供最终汇总节点使用。 */
    private String result;

    /** 该步骤失败时的安全错误信息，成功时为null。 */
    private String errorMessage;
}
