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

import io.gemini.rpc.Response;
import io.gemini.rpc.consumer.processor.task.MessageTask;
import io.gemini.rpc.executor.CloseableExecutor;
import io.gemini.transport.channel.Chan;
import io.gemini.transport.payload.ResponsePayload;
import io.gemini.transport.processor.ConsumerProcessor;

/**
 * The default implementation of consumer's processor.
 *
 * jupiter
 * org.jupiter.rpc.consumer.processor
 *
 * @author jiachun.fjc
 */
public class DefaultConsumerProcessor implements ConsumerProcessor {

    private final CloseableExecutor executor;

    public DefaultConsumerProcessor() {
        this(ConsumerExecutors.executor());
    }

    public DefaultConsumerProcessor(CloseableExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void handleResponse(Chan channel, ResponsePayload responsePayload) throws Exception {
        MessageTask task = new MessageTask(channel, new Response(responsePayload));
        if (executor == null) {
            // ? callers run ?
            channel.addTask(task);
        } else {
            executor.execute(task);
        }
    }

    @Override
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
