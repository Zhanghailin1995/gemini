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
package io.gemini.core.processor.task;

import com.google.common.base.Throwables;
import io.gemini.common.util.ServiceLoader;
import io.gemini.common.util.SystemPropertyUtil;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.core.executor.CloseableExecutor;
import io.gemini.core.executor.ExecutorFactory;
import io.gemini.core.executor.ThreadPoolExecutorFactory;

/**
 * jupiter
 * org.jupiter.rpc.provider.processor
 *
 * @author jiachun.fjc
 */
public class MessageExecutors {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MessageExecutors.class);

    private static final CloseableExecutor executor;

    static {
        String factoryName = SystemPropertyUtil.get("io.gemini.executor.factory", "threadPool");
        ExecutorFactory factory;
        try {
            factory = ServiceLoader.load(ExecutorFactory.class)
                    .find(factoryName);
        } catch (Throwable t) {
            logger.warn("Failed to load provider's executor factory [{}], cause: {}, " +
                    "[ThreadPoolExecutorFactory] will be used as default.", factoryName, Throwables.getStackTraceAsString(t));
            //TODO
            factory = new ThreadPoolExecutorFactory();
        }

        executor = factory.newExecutor("gemini-message-processor");
    }

    public static CloseableExecutor executor() {
        return executor;
    }
}
