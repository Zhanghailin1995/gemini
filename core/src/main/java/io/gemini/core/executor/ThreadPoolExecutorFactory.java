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
package io.gemini.core.executor;

import com.google.common.base.Throwables;
import io.gemini.common.concurrent.RejectedTaskPolicyWithReport;
import io.gemini.common.util.SpiMetadata;
import io.gemini.common.util.SystemPropertyUtil;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Constructor;
import java.util.concurrent.*;

/**
 * Provide a {@link ThreadPoolExecutor} implementation of executor.
 * Thread pool executor factory.
 *
 * jupiter
 * org.jupiter.rpc.executor
 *
 * @author jiachun.fjc
 */
@SpiMetadata(name = "threadPool", priority = 1)
public class ThreadPoolExecutorFactory extends AbstractExecutorFactory {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ThreadPoolExecutorFactory.class);

    @Override
    public CloseableExecutor newExecutor(String name) {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreWorkers(),
                maxWorkers(),
                120L,
                TimeUnit.SECONDS,
                workQueue(),
                threadFactory(name),
                createRejectedPolicy(name, new RejectedTaskPolicyWithReport(name, "gemini")));

        return new CloseableExecutor() {

            @Override
            public void execute(Runnable task) {
                executor.execute(task);
            }

            @Override
            public void shutdown() {
                logger.warn("ThreadPoolExecutorFactory#{} shutdown.", executor);
                executor.shutdownNow();
            }
        };
    }

    private BlockingQueue<Runnable> workQueue() {
        BlockingQueue<Runnable> workQueue = null;
        WorkQueueType queueType = queueType(WorkQueueType.ARRAY_BLOCKING_QUEUE);
        int queueCapacity = queueCapacity();
        switch (queueType) {
            case LINKED_BLOCKING_QUEUE:
                workQueue = new LinkedBlockingQueue<>(queueCapacity);
                break;
            case ARRAY_BLOCKING_QUEUE:
                workQueue = new ArrayBlockingQueue<>(queueCapacity);
                break;
        }

        return workQueue;
    }

    @SuppressWarnings("SameParameterValue")
    private WorkQueueType queueType(WorkQueueType defaultType) {
        WorkQueueType queueType = WorkQueueType.parse(SystemPropertyUtil.get(EXECUTOR_QUEUE_TYPE));

        return queueType == null ? defaultType : queueType;
    }

    private RejectedExecutionHandler createRejectedPolicy(String name, RejectedExecutionHandler defaultHandler) {
        RejectedExecutionHandler handler = null;
        String handlerClass = SystemPropertyUtil.get(THREAD_POOL_REJECTED_HANDLER);
        if (StringUtils.isNotBlank(handlerClass)) {
            try {
                Class<?> cls = Class.forName(handlerClass);
                try {
                    Constructor<?> constructor = cls.getConstructor(String.class, String.class);
                    handler = (RejectedExecutionHandler) constructor.newInstance(name, "jupiter");
                } catch (NoSuchMethodException e) {
                    handler = (RejectedExecutionHandler) cls.newInstance();
                }
            } catch (Exception e) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Construct {} failed, {}.", handlerClass, Throwables.getStackTraceAsString(e));
                }
            }
        }

        return handler == null ? defaultHandler : handler;
    }

    enum WorkQueueType {
        LINKED_BLOCKING_QUEUE,
        ARRAY_BLOCKING_QUEUE;

       static WorkQueueType parse(String name) {
            for (WorkQueueType type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }
}
