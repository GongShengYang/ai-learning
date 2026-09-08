package com.gsy.ai.workflow.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

/** 模型为复合问题生成的结构化执行计划。 */
@Data
@JsonClassDescription("复合问题的执行计划")
@JsonPropertyOrder({"goal", "steps"})
public class WorkflowPlan {
    /** goal：整个计划最终要完成的目标。 */
    @JsonPropertyDescription("计划的总体目标，使用中文简要描述")
    private String goal;

    /** steps：Executor将按列表顺序执行的步骤，当前限制为2到3步。 */
    @JsonPropertyDescription("按执行顺序排列的步骤列表，最少2步，最多3步")
    private List<WorkflowPlanStep> steps;
}
