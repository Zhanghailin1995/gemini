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
package io.gemini.rpc.consumer.dispatcher;

import io.gemini.rpc.Client;
import io.gemini.rpc.DispatchType;
import io.gemini.rpc.Request;
import io.gemini.rpc.consumer.future.DefaultInvokeFuture;
import io.gemini.rpc.consumer.future.DefaultInvokeFutureGroup;
import io.gemini.rpc.consumer.future.InvokeFuture;
import io.gemini.rpc.model.metadata.MessageWrapper;
import io.gemini.serialization.Serializer;
import io.gemini.serialization.SerializerType;
import io.gemini.transport.CodecConfig;
import io.gemini.transport.channel.Chan;
import io.gemini.transport.channel.ChanGroup;

/**
 * 组播方式派发消息.
 *
 * jupiter
 * org.jupiter.rpc.consumer.dispatcher
 *
 * @author jiachun.fjc
 */
public class DefaultBroadcastDispatcher extends AbstractDispatcher {

    public DefaultBroadcastDispatcher(Client client, SerializerType serializerType) {
        super(client, serializerType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> InvokeFuture<T> dispatch(Request request, Class<T> returnType) {
        // stack copy
        final Serializer _serializer = serializer();
        final MessageWrapper message = request.message();

        ChanGroup[] groups = groups(message.getMetadata());
        Chan[] channels = new Chan[groups.length];
        for (int i = 0; i < groups.length; i++) {
            channels[i] = groups[i].next();
        }

        byte s_code = _serializer.code();
        // 在业务线程中序列化, 减轻IO线程负担
        boolean isLowCopy = CodecConfig.isCodecLowCopy();
        if (!isLowCopy) {
            byte[] bytes = _serializer.writeObject(message);
            request.bytes(s_code, bytes);
        }

        DefaultInvokeFuture<T>[] futures = new DefaultInvokeFuture[channels.length];
        for (int i = 0; i < channels.length; i++) {
            Chan channel = channels[i];
            /*if (isLowCopy) {
                OutputBuf outputBuf =
                        _serializer.writeObject(channel.allocOutputBuf(), message);
                request.outputBuf(s_code, outputBuf);
            }*/
            futures[i] = write(channel, request, returnType, DispatchType.BROADCAST);
        }

        return DefaultInvokeFutureGroup.with(futures);
    }
}
