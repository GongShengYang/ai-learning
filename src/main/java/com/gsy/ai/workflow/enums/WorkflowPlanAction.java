package com.gsy.ai.workflow.enums;

/**
 * Planner允许生成的步骤类型，也是Executor可以执行的动作白名单。
 * 模型不能通过文本指定任意Java方法，只能从这些安全动作中选择。
 */
public enum WorkflowPlanAction {
    /** 检索企业知识库，并根据召回资料回答该子问题。 */
    KNOWLEDGE_SEARCH,

    /** 查询知识库当前的文档总数。 */
    DOCUMENT_COUNT,

    /** 不依赖知识库，由模型完成普通文本子任务。 */
    GENERAL_CHAT
}
