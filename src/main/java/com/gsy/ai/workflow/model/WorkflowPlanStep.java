package com.gsy.ai.workflow.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gsy.ai.workflow.enums.WorkflowPlanAction;
import lombok.Data;

/** Planner生成的一条执行步骤。 */
@Data
public class WorkflowPlanStep {
    /** stepNumber：步骤序号，必须从1开始连续递增。 */
    @JsonPropertyDescription("步骤序号，从1开始，并按照实际执行顺序连续递增")
    private Integer stepNumber;

    /** action：动作，决定Executor调用哪一种受控能力。 */
    @JsonPropertyDescription("步骤动作，只能使用WorkflowPlanAction中定义的枚举值")
    private WorkflowPlanAction action;

    /** instruction：该步骤要独立完成的明确任务。 */
    @JsonPropertyDescription("该步骤的具体任务，使用中文描述，必须能够脱离其他步骤单独执行")
    private String instruction;
}
