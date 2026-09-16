package com.gsy.ai.workflow.service;

import com.gsy.ai.workflow.dto.WorkflowStreamEvent;

/**
 * Workflow与SSE Controller之间的回调接口。
 * Service只负责产生事件，不依赖Web层的SseEmitter；Controller决定收到事件后如何发送给前端。
 */
@FunctionalInterface
public interface WorkflowStreamEventListener {
    /**
     * onEvent不是网络发送方法，而是普通Java回调方法。
     * Service调用它时，会立即执行Controller传入的Lambda，例如event -> emitter.send(...)。
     */
    void onEvent(WorkflowStreamEvent event);

    /**
     * 同步接口不需要推送事件，但仍要复用同一套Workflow代码，因此提供一个什么都不做的监听器。
     * 这样Service内部不需要到处判断eventListener是否为null。
     */
    static WorkflowStreamEventListener noop() {
        return event -> {
        };
    }
}
