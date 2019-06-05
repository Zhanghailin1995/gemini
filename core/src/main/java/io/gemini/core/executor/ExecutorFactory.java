package io.gemini.core.executor;

/**
 * gemini
 * io.gemini.core.executor.ExecutorFactory
 *
 * @author zhanghailin
 */
public interface ExecutorFactory {

    CloseableExecutor newExecutor(String name);

    String EXECUTOR_CORE_WORKERS           = "io.gemini.executor.factory.core.workers";
    String EXECUTOR_MAX_WORKERS            = "io.gemini.executor.factory.max.workers";
    String EXECUTOR_QUEUE_TYPE             = "io.gemini.executor.factory.queue.type";
    String EXECUTOR_QUEUE_CAPACITY         = "io.gemini.executor.factory.queue.capacity";
    String DISRUPTOR_WAIT_STRATEGY_TYPE    = "io.gemini.executor.factory.disruptor.wait.strategy.type";
    String THREAD_POOL_REJECTED_HANDLER    = "io.gemini.executor.factory.thread.pool.rejected.handler";
}
