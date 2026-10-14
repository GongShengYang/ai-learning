package com.gsy.ai.workflow.controller;

import com.gsy.ai.common.Result;
import com.gsy.ai.workflow.dto.WorkflowQuestionRequest;
import com.gsy.ai.workflow.dto.WorkflowQuestionResponse;
import com.gsy.ai.workflow.dto.WorkflowStreamEvent;
import com.gsy.ai.workflow.enums.WorkflowStatus;
import com.gsy.ai.workflow.service.WorkflowService;
import com.gsy.ai.workflow.service.WorkflowStreamEventListener;
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

        // 这里只负责注册回调，完成、超时和异常分别如何处理，都放在有名字的方法中。
        registerEmitterCallbacks(emitter, closed, finished, taskReference);

        try {
            // 把完整Workflow提交到后台，Controller随后立刻return emitter，不等待模型回答完成。
            StreamWorkflowTask streamTask = new StreamWorkflowTask(
                    request, emitter, closed, finished, taskReference);
            Future<?> task = workflowRequestExecutor.submit(streamTask);
            // 保存Future，供上面的完成、超时和异常回调取消任务。
            taskReference.set(task);
        } catch (RejectedExecutionException e) {
            sendEvent(emitter, closed, taskReference, "error", Map.of("message", "当前请求较多，请稍后重试"));
            finished.set(true);
            emitter.complete();
        }
        return emitter;
    }

    /**
     * 给SseEmitter登记三个生命周期回调。
     * 登记不等于现在执行：Spring会在连接真正完成、超时或出错时，再调用对应方法。
     * Lambda这里只转发参数，没有隐藏具体业务判断，实际处理逻辑都在下面三个方法中。
     */
    private void registerEmitterCallbacks(SseEmitter emitter, AtomicBoolean closed,
                                          AtomicBoolean finished,
                                          AtomicReference<Future<?>> taskReference) {
        emitter.onCompletion(() -> handleEmitterCompletion(closed, finished, taskReference));
        emitter.onTimeout(() -> handleEmitterTimeout(emitter, closed, taskReference));
        emitter.onError(error -> handleEmitterError(closed, taskReference, error));
    }

    /** SSE正常结束或客户端提前断开时，由Spring回调该方法。 */
    private void handleEmitterCompletion(AtomicBoolean closed, AtomicBoolean finished,
                                         AtomicReference<Future<?>> taskReference) {
        closed.set(true);
        // Workflow尚未执行完成，说明连接属于提前结束，应尝试取消后台任务。
        if (!finished.get()) {
            cancelTask(taskReference);
        }
    }

    /** SSE超过五分钟仍未结束时，由Spring回调该方法。 */
    private void handleEmitterTimeout(SseEmitter emitter, AtomicBoolean closed,
                                      AtomicReference<Future<?>> taskReference) {
        closed.set(true);
        cancelTask(taskReference);
        emitter.complete();
    }

    /** 网络断开或发送事件失败时，由Spring回调该方法。 */
    private void handleEmitterError(AtomicBoolean closed, AtomicReference<Future<?>> taskReference,
                                    Throwable error) {
        closed.set(true);
        cancelTask(taskReference);
    }

    /** 在线程池中执行完整Workflow，Servlet线程可以立即返回SSE连接。 */
    private void runStreamWorkflow(WorkflowQuestionRequest request, SseEmitter emitter,
                                   AtomicBoolean closed, AtomicBoolean finished,
                                   AtomicReference<Future<?>> taskReference) {
        try {
            // 使用有名字的监听器对象接收Service事件，避免把SSE发送逻辑藏在Lambda参数中。
            SseWorkflowEventListener eventListener = new SseWorkflowEventListener(
                    emitter, closed, taskReference);
            WorkflowQuestionResponse response = workflowService.answerWithEvents(
                    request == null ? null : request.getUserId(),
                    request == null ? null : request.getConversationId(),
                    request == null ? null : request.getQuestion(),
                    eventListener);

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

    /**
     * 线程池中执行的完整流式Workflow任务。
     * Controller先创建该对象，再把它交给线程池；线程池随后会自动调用run()。
     */
    private final class StreamWorkflowTask implements Runnable {
        private final WorkflowQuestionRequest request;
        private final SseEmitter emitter;
        private final AtomicBoolean closed;
        private final AtomicBoolean finished;
        private final AtomicReference<Future<?>> taskReference;

        private StreamWorkflowTask(WorkflowQuestionRequest request, SseEmitter emitter,
                                   AtomicBoolean closed, AtomicBoolean finished,
                                   AtomicReference<Future<?>> taskReference) {
            this.request = request;
            this.emitter = emitter;
            this.closed = closed;
            this.finished = finished;
            this.taskReference = taskReference;
        }

        @Override
        public void run() {
            runStreamWorkflow(request, emitter, closed, finished, taskReference);
        }
    }

    /** Service调用onEvent()时，把Workflow事件转交给Controller写入SSE连接。 */
    private final class SseWorkflowEventListener implements WorkflowStreamEventListener {
        private final SseEmitter emitter;
        private final AtomicBoolean closed;
        private final AtomicReference<Future<?>> taskReference;

        private SseWorkflowEventListener(SseEmitter emitter, AtomicBoolean closed,
                                         AtomicReference<Future<?>> taskReference) {
            this.emitter = emitter;
            this.closed = closed;
            this.taskReference = taskReference;
        }

        @Override
        public void onEvent(WorkflowStreamEvent event) {
            sendWorkflowEvent(emitter, closed, taskReference, event);
        }
    }
}
