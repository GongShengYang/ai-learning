package com.gsy.ai.workflow.service;

import com.gsy.ai.workflow.model.WorkflowPlan;

/** 使用模型把一个复合问题拆成受约束的执行计划。 */
public interface WorkflowPlannerService {
    WorkflowPlan createPlan(String question);
}
