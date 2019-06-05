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

import io.gemini.common.concurrent.disruptor.TaskDispatcher;
import io.gemini.common.concurrent.disruptor.WaitStrategyType;
import io.gemini.common.util.SpiMetadata;
import io.gemini.common.util.SystemPropertyUtil;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;

/**
 * Provide a disruptor implementation of executor.
 * <p>
 * jupiter
 * org.jupiter.rpc.executor
 *
 * @author jiachun.fjc
 */
@SpiMetadata(name = "disruptor")
public class DisruptorExecutorFactory extends AbstractExecutorFactory {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DisruptorExecutorFactory.class);

    @Override
    public CloseableExecutor newExecutor(String name) {
        final TaskDispatcher executor = new TaskDispatcher(
                coreWorkers(),
                threadFactory(name),
                queueCapacity(),
                maxWorkers(),
                waitStrategyType(WaitStrategyType.LITE_BLOCKING_WAIT),
                "gemini");

        return new CloseableExecutor() {

            @Override
            public void execute(Runnable task) {
                executor.execute(task);
            }

            @Override
            public void shutdown() {
                logger.warn("DisruptorExecutorFactory#{} shutdown.", executor);
                executor.shutdown();
            }
        };
    }

    @SuppressWarnings("SameParameterValue")
    private WaitStrategyType waitStrategyType(WaitStrategyType defaultType) {
        WaitStrategyType strategyType = WaitStrategyType.parse(SystemPropertyUtil.get(DISRUPTOR_WAIT_STRATEGY_TYPE));
        return strategyType == null ? defaultType : strategyType;
    }
}
