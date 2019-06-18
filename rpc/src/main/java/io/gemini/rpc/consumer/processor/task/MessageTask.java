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
package io.gemini.rpc.consumer.processor.task;

import com.google.common.base.Throwables;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.rpc.Response;
import io.gemini.rpc.consumer.future.DefaultInvokeFuture;
import io.gemini.rpc.exception.GeminiSerializationException;
import io.gemini.rpc.model.metadata.ResultWrapper;
import io.gemini.serialization.Serializer;
import io.gemini.serialization.SerializerFactory;
import io.gemini.transport.CodecConfig;
import io.gemini.transport.Status;
import io.gemini.transport.channel.Chan;
import io.gemini.transport.payload.ResponsePayload;

/**
 * jupiter
 * org.jupiter.rpc.consumer.processor.task
 *
 * @author jiachun.fjc
 */
public class MessageTask implements Runnable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MessageTask.class);

    private final Chan channel;
    private final Response response;

    public MessageTask(Chan channel, Response response) {
        this.channel = channel;
        this.response = response;
    }

    @Override
    public void run() {
        // stack copy
        final Response _response = response;
        final ResponsePayload _responsePayload = _response.payload();

        byte s_code = _response.serializerCode();

        Serializer serializer = SerializerFactory.getSerializer(s_code);
        ResultWrapper wrapper;
        try {
            if (CodecConfig.isCodecLowCopy()) {
                //TODO low copy
                /*InputBuf inputBuf = _responsePayload.inputBuf();
                wrapper = serializer.readObject(inputBuf, ResultWrapper.class);*/
                wrapper = null;
            } else {
                byte[] bytes = _responsePayload.bytes();
                wrapper = serializer.readObject(bytes, ResultWrapper.class);
            }
            _responsePayload.clear();
        } catch (Throwable t) {
            logger.error("Deserialize object failed: {}, {}.", channel.remoteAddress(), Throwables.getStackTraceAsString(t));

            _response.status(Status.DESERIALIZATION_FAIL);
            wrapper = new ResultWrapper();
            wrapper.setError(new GeminiSerializationException(t));
        }
        _response.result(wrapper);

        DefaultInvokeFuture.received(channel, _response);
    }
}
