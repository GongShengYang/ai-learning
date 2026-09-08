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
}
