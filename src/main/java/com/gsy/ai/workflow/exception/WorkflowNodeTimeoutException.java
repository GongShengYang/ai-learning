package com.gsy.ai.workflow.exception;

/** 节点在规定时间内没有执行完成时抛出的异常。 */
public class WorkflowNodeTimeoutException extends RuntimeException {
    public WorkflowNodeTimeoutException(String message) {
        super(message);
    }
}
