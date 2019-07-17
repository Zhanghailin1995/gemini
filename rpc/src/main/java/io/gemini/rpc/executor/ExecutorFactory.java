/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gemini.rpc.executor;

import io.gemini.rpc.consumer.processor.ConsumerExecutorFactory;
import io.gemini.rpc.provider.processor.ProviderExecutorFactory;

/**
 * Executor factory.
 *
 * io.gemini
 * org.io.gemini.rpc.executor
 *
 * @author jiachun.fjc
 */
public interface ExecutorFactory extends ConsumerExecutorFactory, ProviderExecutorFactory {

    CloseableExecutor newExecutor(Target target, String name);

    enum Target {
        CONSUMER,
        PROVIDER
    }

    String CONSUMER_EXECUTOR_CORE_WORKERS           = "gemini.executor.factory.consumer.core.workers";
    String PROVIDER_EXECUTOR_CORE_WORKERS           = "gemini.executor.factory.provider.core.workers";
    String CONSUMER_EXECUTOR_MAX_WORKERS            = "gemini.executor.factory.consumer.max.workers";
    String PROVIDER_EXECUTOR_MAX_WORKERS            = "gemini.executor.factory.provider.max.workers";
    String CONSUMER_EXECUTOR_QUEUE_TYPE             = "gemini.executor.factory.consumer.queue.type";
    String PROVIDER_EXECUTOR_QUEUE_TYPE             = "gemini.executor.factory.provider.queue.type";
    String CONSUMER_EXECUTOR_QUEUE_CAPACITY         = "gemini.executor.factory.consumer.queue.capacity";
    String PROVIDER_EXECUTOR_QUEUE_CAPACITY         = "gemini.executor.factory.provider.queue.capacity";
    String CONSUMER_DISRUPTOR_WAIT_STRATEGY_TYPE    = "gemini.executor.factory.consumer.disruptor.wait.strategy.type";
    String PROVIDER_DISRUPTOR_WAIT_STRATEGY_TYPE    = "gemini.executor.factory.provider.disruptor.wait.strategy.type";
    String CONSUMER_THREAD_POOL_REJECTED_HANDLER    = "gemini.executor.factory.consumer.thread.pool.rejected.handler";
    String PROVIDER_THREAD_POOL_REJECTED_HANDLER    = "gemini.executor.factory.provider.thread.pool.rejected.handler";
    String EXECUTOR_AFFINITY_THREAD                 = "gemini.executor.factory.affinity.thread";
}
