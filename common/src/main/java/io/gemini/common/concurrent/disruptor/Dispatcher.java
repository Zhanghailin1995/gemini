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
package io.gemini.common.concurrent.disruptor;

import io.gemini.common.contants.Constants;

/**
 * Task message dispatcher.
 *
 * jupiter
 * org.jupiter.common.concurrent.disruptor
 *
 * @author jiachun.fjc
 */
public interface Dispatcher<T> {

    int BUFFER_SIZE = 32768;
    int MAX_NUM_WORKERS = Constants.AVAILABLE_PROCESSORS << 3;

    /**
     * Dispatch a task message.
     */
    boolean dispatch(T message);

    /**
     * Shutdown
     */
    void shutdown();
}
