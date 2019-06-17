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
package io.gemini.rpc.consumer.processor;

import com.google.common.base.Throwables;
import io.gemini.common.util.ServiceLoader;
import io.gemini.common.util.SystemPropertyUtil;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.rpc.executor.CallerRunsExecutorFactory;
import io.gemini.rpc.executor.CloseableExecutor;
import io.gemini.rpc.executor.ExecutorFactory;

/**
 * jupiter
 * org.jupiter.rpc.consumer.processor
 *
 * @author jiachun.fjc
 */
public class ConsumerExecutors {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ConsumerExecutors.class);

    private static final CloseableExecutor executor;

    static {
        String factoryName = SystemPropertyUtil.get("gemini.executor.factory.consumer.factory_name", "callerRuns");
        ExecutorFactory factory;
        try {
            factory = (ExecutorFactory) ServiceLoader.load(ConsumerExecutorFactory.class)
                    .find(factoryName);
        } catch (Throwable t) {
            logger.warn("Failed to load consumer's executor factory [{}], cause: {}, " +
                            "[CallerRunsExecutorFactory] will be used as default.", factoryName, Throwables.getStackTraceAsString(t));

            factory = new CallerRunsExecutorFactory();
        }

        executor = factory.newExecutor(ExecutorFactory.Target.CONSUMER, "gemini-consumer-processor");
    }

    public static CloseableExecutor executor() {
        return executor;
    }
}
