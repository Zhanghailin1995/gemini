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
import io.gemini.rpc.consumer.future.InvokeFuture;
import io.gemini.rpc.loadbalance.LoadBalancer;
import io.gemini.rpc.model.metadata.MessageWrapper;
import io.gemini.serialization.Serializer;
import io.gemini.serialization.SerializerType;
import io.gemini.transport.CodecConfig;
import io.gemini.transport.channel.Chan;

/**
 * 单播方式派发消息.
 *
 * jupiter
 * org.jupiter.rpc.consumer.dispatcher
 *
 * @author jiachun.fjc
 */
public class DefaultRoundDispatcher extends AbstractDispatcher {

    public DefaultRoundDispatcher(
            Client client, LoadBalancer loadBalancer, SerializerType serializerType) {
        super(client, loadBalancer, serializerType);
    }

    @Override
    public <T> InvokeFuture<T> dispatch(Request request, Class<T> returnType) {
        // stack copy
        final Serializer _serializer = serializer();
        final MessageWrapper message = request.message();

        // 通过软负载均衡选择一个channel
        Chan channel = select(message.getMetadata());

        byte s_code = _serializer.code();
        // 在业务线程中序列化, 减轻IO线程负担
        if (CodecConfig.isCodecLowCopy()) {
            //TODO low copy
            /*OutputBuf outputBuf =
                    _serializer.writeObject(channel.allocOutputBuf(), message);
            request.outputBuf(s_code, outputBuf);*/
        } else {
            byte[] bytes = _serializer.writeObject(message);
            request.bytes(s_code, bytes);
        }

        return write(channel, request, returnType, DispatchType.ROUND);
    }
}
