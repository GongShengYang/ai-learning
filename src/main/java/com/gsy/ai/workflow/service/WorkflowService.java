package com.gsy.ai.workflow.service;

import com.gsy.ai.workflow.dto.WorkflowQuestionResponse;

/** 基于Structured Output、确定路由和Conversation Memory的问答服务。 */
public interface WorkflowService {
    /**
     * @param userId 当前用户ID
     * @param conversationId 新会话为空，继续会话时传入已有ID
     * @param question 用户当前问题
     */
    WorkflowQuestionResponse answer(Long userId, String conversationId, String question);

    /**
     * 执行同一套Workflow，并把节点状态和回答片段实时通知给SSE层。
     * 方法本身仍是同步执行，由Controller放入独立请求线程池，避免占用Servlet请求线程。
     */
    WorkflowQuestionResponse answerWithEvents(Long userId, String conversationId, String question,
                                              WorkflowStreamEventListener eventListener);
}
