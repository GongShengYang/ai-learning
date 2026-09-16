package com.gsy.ai.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Workflow节点执行线程池配置。 */
@Configuration
public class WorkflowExecutorConfig {
    private static final int WORKER_COUNT = 4;
    private static final int REQUEST_CORE_COUNT = 2;
    private static final int REQUEST_MAX_COUNT = 8;
    private static final int QUEUE_CAPACITY = 50;

    /**
     * 使用独立且有界的线程池执行节点：
     * 1. 避免节点阻塞占用公共线程池；
     * 2. 队列达到上限后拒绝新任务，防止故障时无限堆积导致内存耗尽；
     * 3. Spring关闭应用时调用shutdown()释放线程。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService workflowNodeExecutor() {
        AtomicInteger threadNumber = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                WORKER_COUNT,
                WORKER_COUNT,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                task -> new Thread(task, "workflow-node-" + threadNumber.getAndIncrement()),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * 一个普通Controller通常是“Servlet线程执行完业务后一次性返回”。
     * SSE需要先把连接交还给浏览器，再在后台继续执行几十秒，因此把完整Workflow放进该请求线程池。
     *
     * 两个线程池的职责：
     * 1. workflowRequestExecutor：承载一整个SSE请求，从开始执行到最终complete；
     * 2. workflowNodeExecutor：执行其中某一个带超时控制的节点。
     *
     * 二者不能共用：如果外层SSE请求占满全部线程，又等待内层节点取得线程，就可能互相等待。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService workflowRequestExecutor() {
        AtomicInteger threadNumber = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                REQUEST_CORE_COUNT,
                REQUEST_MAX_COUNT,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                task -> new Thread(task, "workflow-request-" + threadNumber.getAndIncrement()),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
