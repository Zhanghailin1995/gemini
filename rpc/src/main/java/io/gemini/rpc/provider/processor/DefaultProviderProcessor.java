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
package io.gemini.rpc.provider.processor;

import com.google.common.base.Throwables;
import io.gemini.common.util.ThrowUtil;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.rpc.Request;
import io.gemini.rpc.executor.CloseableExecutor;
import io.gemini.rpc.flow.control.FlowController;
import io.gemini.rpc.model.metadata.ResultWrapper;
import io.gemini.rpc.provider.LookupService;
import io.gemini.rpc.provider.processor.task.MessageTask;
import io.gemini.serialization.Serializer;
import io.gemini.serialization.SerializerFactory;
import io.gemini.transport.CodecConfig;
import io.gemini.transport.Status;
import io.gemini.transport.channel.Chan;
import io.gemini.transport.channel.FutureListener;
import io.gemini.transport.payload.RequestPayload;
import io.gemini.transport.payload.ResponsePayload;
import io.gemini.transport.processor.ProviderProcessor;

/**
 * jupiter
 * org.jupiter.rpc.provider.processor
 *
 * @author jiachun.fjc
 */
public abstract class DefaultProviderProcessor implements ProviderProcessor, LookupService, FlowController<Request> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultProviderProcessor.class);

    private final CloseableExecutor executor;

    public DefaultProviderProcessor() {
        this(ProviderExecutors.executor());
    }

    public DefaultProviderProcessor(CloseableExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void handleRequest(Chan channel, RequestPayload requestPayload) throws Exception {
        MessageTask task = new MessageTask(this, channel, new Request(requestPayload));
        if (executor == null) {
            channel.addTask(task);
        } else {
            executor.execute(task);
        }
    }

    @Override
    public void handleException(Chan channel, RequestPayload request, Status status, Throwable cause) {
        logger.error("An exception was caught while processing request: {}, {}.",
                channel.remoteAddress(), Throwables.getStackTraceAsString(cause));

        doHandleException(
                channel, request.invokeId(), request.serializerCode(), status.value(), cause, false);
    }

    @Override
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    public void handleException(Chan channel, Request request, Status status, Throwable cause) {
        logger.error("An exception was caught while processing request: {}, {}.",
                channel.remoteAddress(), Throwables.getStackTraceAsString(cause));

        doHandleException(
                channel, request.invokeId(), request.serializerCode(), status.value(), cause, false);
    }

    public void handleRejected(Chan channel, Request request, Status status, Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("Service rejected: {}, {}.", channel.remoteAddress(), Throwables.getStackTraceAsString(cause));
        }

        doHandleException(
                channel, request.invokeId(), request.serializerCode(), status.value(), cause, true);
    }

    private void doHandleException(
            Chan channel, long invokeId, byte s_code, byte status, Throwable cause, boolean closeChannel) {

        ResultWrapper result = new ResultWrapper();
        // 截断cause, 避免客户端无法找到cause类型而无法序列化
        result.setError(ThrowUtil.cutCause(cause));

        Serializer serializer = SerializerFactory.getSerializer(s_code);

        ResponsePayload response = new ResponsePayload(invokeId);
        response.status(status);
        if (CodecConfig.isCodecLowCopy()) {
            //TODO low copy
            /*OutputBuf outputBuf =
                    serializer.writeObject(channel.allocOutputBuf(), result);
            response.outputBuf(s_code, outputBuf);*/
        } else {
            byte[] bytes = serializer.writeObject(result);
            response.bytes(s_code, bytes);
        }

        if (closeChannel) {
            channel.write(response, Chan.CLOSE);
        } else {
            channel.write(response, new FutureListener<Chan>() {

                @Override
                public void operationSuccess(Chan channel) throws Exception {
                    logger.debug("Service error message sent out: {}.", channel);
                }

                @Override
                public void operationFailure(Chan channel, Throwable cause) throws Exception {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Service error message sent failed: {}, {}.", channel,
                                Throwables.getStackTraceAsString(cause));
                    }
                }
            });
        }
    }
}
