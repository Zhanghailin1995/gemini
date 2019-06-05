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

import io.gemini.common.concurrent.DefaultThreadFactory;
import io.gemini.common.contants.Constants;
import io.gemini.common.util.SystemPropertyUtil;

import java.util.concurrent.ThreadFactory;

/**
 * jupiter
 * org.jupiter.rpc.executor
 *
 * @author jiachun.fjc
 */
public abstract class AbstractExecutorFactory implements ExecutorFactory {

    protected ThreadFactory threadFactory(String name) {
        return new DefaultThreadFactory(name);
    }

    protected int coreWorkers() {
        return SystemPropertyUtil.getInt(EXECUTOR_CORE_WORKERS, Constants.AVAILABLE_PROCESSORS << 1);
    }

    protected int maxWorkers() {
        return SystemPropertyUtil.getInt(EXECUTOR_MAX_WORKERS, 512);
    }

    protected int queueCapacity() {
        return SystemPropertyUtil.getInt(EXECUTOR_QUEUE_CAPACITY, 131072);
    }
}
