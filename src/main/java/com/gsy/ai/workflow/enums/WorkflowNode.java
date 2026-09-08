package com.gsy.ai.workflow.enums;

/** 当前Workflow包含的固定执行节点。 */
public enum WorkflowNode {
    /** 创建或校验会话，并读取当前会话的历史消息。 */
    CONVERSATION_PREPARATION,

    /** 保存本轮用户消息；即使后续模型失败，也保留用户真实输入。 */
    USER_MESSAGE_PERSISTENCE,

    /** 使用Structured Output识别用户意图。 */
    INTENT_CLASSIFICATION,

    /** 根据意图和置信度决定执行分支。 */
    ROUTE_DECISION,

    /** 复合任务专用：模型把用户目标拆成2到3个受约束步骤。 */
    TASK_PLANNING,

    /** 执行知识检索、文档统计、普通聊天等具体分支。 */
    ROUTE_EXECUTION,

    /** 保存成功生成的助手回答，并更新会话最后活跃时间。 */
    ASSISTANT_MESSAGE_PERSISTENCE
}
