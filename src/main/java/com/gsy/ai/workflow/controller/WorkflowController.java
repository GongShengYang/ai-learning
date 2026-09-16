package com.gsy.ai.workflow.controller;

import com.gsy.ai.common.Result;
import com.gsy.ai.workflow.dto.WorkflowQuestionRequest;
import com.gsy.ai.workflow.dto.WorkflowQuestionResponse;
import com.gsy.ai.workflow.dto.WorkflowStreamEvent;
import com.gsy.ai.workflow.enums.WorkflowStatus;
import com.gsy.ai.workflow.service.WorkflowService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Workflow问答接口，包含确定路由、执行轨迹和Conversation Memory。 */
@RestController
@RequestMapping("/ai/workflow")
public class WorkflowController {
    private static final long SSE_TIMEOUT_MS = 300_000L;

    @Resource
    private WorkflowService workflowService;

    /** 单独承载完整SSE请求，避免长时间占用Servlet请求线程。 */
    @Resource
    private ExecutorService workflowRequestExecutor;

    @PostMapping("/question")
    public Result<WorkflowQuestionResponse> question(@RequestBody WorkflowQuestionRequest request) {
        return Result.success(workflowService.answer(
                request == null ? null : request.getUserId(),
                request == null ? null : request.getConversationId(),
                request == null ? null : request.getQuestion()));
    }

    /**
     * POST流式接口：节点状态和模型文本会通过同一条SSE连接持续返回。
     * 浏览器原生EventSource只支持GET，因此调试页面使用fetch读取POST响应流。
     */
    @PostMapping(value = "/question/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter questionStream(@RequestBody WorkflowQuestionRequest request) {
        // SseEmitter代表一条暂时不关闭的HTTP响应，5分钟内可以多次调用send()向前端写数据。
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 下面的回调、请求线程和节点线程可能并发访问这些状态，所以使用Atomic类型保证线程可见性。
        // closed=true表示SSE连接已经不可写，后续sendEvent会直接跳过。
        AtomicBoolean closed = new AtomicBoolean(false);
        // finished=true表示Workflow已经正常得到最终结果，用于区分“正常完成”和“中途断开”。
        AtomicBoolean finished = new AtomicBoolean(false);
        // 保存后台完整Workflow任务的Future，连接断开时可以通过cancel(true)尝试中断它。
        AtomicReference<Future<?>> taskReference = new AtomicReference<>();

        /*
         * onCompletion：SSE请求生命周期彻底结束时触发。
         * 正常执行完调用emitter.complete()会触发；浏览器提前断开也可能触发。
         * 如果业务还没有finished，说明属于提前结束，需要尝试取消后台Workflow。
         */
        emitter.onCompletion(() -> {
            closed.set(true);
            if (!finished.get()) {
                cancelTask(taskReference);
            }
        });

        /*
         * onTimeout：SseEmitter超过SSE_TIMEOUT_MS仍未结束时触发。
         * 此时连接已经不能继续无限等待，所以标记关闭、取消后台任务并结束Emitter。
         */
        emitter.onTimeout(() -> {
            closed.set(true);
            cancelTask(taskReference);
            emitter.complete();
        });

        /*
         * onError：向客户端写数据时发生网络异常等错误时触发。
         * 前端已经收不到后续内容，继续运行Workflow只会浪费资源，因此尝试取消后台任务。
         */
        emitter.onError(error -> {
            closed.set(true);
            cancelTask(taskReference);
        });

        try {
            // 把完整Workflow提交到后台，Controller随后立刻return emitter，不等待模型回答完成。
            Future<?> task = workflowRequestExecutor.submit(() -> runStreamWorkflow(
                    request, emitter, closed, finished, taskReference));
            // 保存Future，供上面的完成、超时和异常回调取消任务。
            taskReference.set(task);
        } catch (RejectedExecutionException e) {
            sendEvent(emitter, closed, taskReference, "error", Map.of("message", "当前请求较多，请稍后重试"));
            finished.set(true);
            emitter.complete();
        }
        return emitter;
    }

    /** 在线程池中执行完整Workflow，Servlet线程可以立即返回SSE连接。 */
    private void runStreamWorkflow(WorkflowQuestionRequest request, SseEmitter emitter,
                                   AtomicBoolean closed, AtomicBoolean finished,
                                   AtomicReference<Future<?>> taskReference) {
        try {
            WorkflowQuestionResponse response = workflowService.answerWithEvents(
                    request == null ? null : request.getUserId(),
                    request == null ? null : request.getConversationId(),
                    request == null ? null : request.getQuestion(),
                    // 这个Lambda就是eventListener.onEvent(event)被调用时真正执行的代码。
                    event -> sendWorkflowEvent(emitter, closed, taskReference, event));

            // Service返回说明整个Workflow结束；根据最终状态发送completed或error事件。
            String eventName = response.getStatus() == WorkflowStatus.SUCCESS ? "completed" : "error";
            sendEvent(emitter, closed, taskReference, eventName, response);
        } catch (RuntimeException e) {
            // SSE响应已经开始后不能再交给全局异常处理器，只能通过error事件通知前端。
            sendEvent(emitter, closed, taskReference, "error", Map.of("message", "流式问题处理失败"));
        } finally {
            finished.set(true);
            if (!closed.get()) {
                emitter.complete();
            }
        }
    }

    private void sendWorkflowEvent(SseEmitter emitter, AtomicBoolean closed,
                                   AtomicReference<Future<?>> taskReference,
                                   WorkflowStreamEvent event) {
        // type可能是node、plan-step、source、route-output-reset或content，同时作为SSE的event名称发送。
        sendEvent(emitter, closed, taskReference, event.getType(), event);
    }

    /** 统一发送SSE事件；写入失败通常表示客户端已经断开。 */
    private void sendEvent(SseEmitter emitter, AtomicBoolean closed,
                           AtomicReference<Future<?>> taskReference,
                           String eventName, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            // 只有emitter.send()才真正把事件序列化并写入保持中的HTTP连接。
            emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            closed.set(true);
            cancelTask(taskReference);
        }
    }

    private void cancelTask(AtomicReference<Future<?>> taskReference) {
        Future<?> task = taskReference.get();
        if (task != null && !task.isDone()) {
            // true表示向执行线程发送interrupt；它是尽力取消，不能强制杀死底层模型HTTP请求。
            task.cancel(true);
        }
    }
}
