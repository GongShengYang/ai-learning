package com.gsy.ai.structuredoutput.enums;

/**
 * 用户问题的业务意图。
 */
public enum QuestionIntent {
    /** 查询知识库正文、项目资料、制度、技术记录或解决方案。 */
    KNOWLEDGE_SEARCH,

    /** 查询知识库中的文档数量或知识库规模。 */
    DOCUMENT_COUNT,

    /** 一个问题包含两个或以上子任务，并且至少一个子任务需要知识库或文档统计。 */
    COMPOSITE_TASK,

    /** 不依赖企业知识库也能回答的普通聊天或通用知识问题。 */
    GENERAL_CHAT,

    /** 问题意图不清晰，或者当前分类体系无法处理。 */
    UNSUPPORTED
}
