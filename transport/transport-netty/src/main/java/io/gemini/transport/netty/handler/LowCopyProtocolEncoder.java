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
package io.gemini.transport.netty.handler;

import io.gemini.common.util.Reflects;
import io.gemini.transport.TransportProtocol;
import io.gemini.transport.payload.PayloadHolder;
import io.gemini.transport.payload.RequestPayload;
import io.gemini.transport.payload.ResponsePayload;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;

/**
 * <pre>
 * **************************************************************************************************
 *                                          Protocol
 *  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
 *       2   │   1   │    1   │     8     │      4      │
 *  ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤
 *           │       │        │           │             │
 *  │  MAGIC   Sign    Status   Invoke Id    Body Size                    Body Content              │
 *           │       │        │           │             │
 *  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
 *
 * 消息头16个字节定长
 * = 2 // magic = (short) 0xbabe
 * + 1 // 消息标志位, 低地址4位用来表示消息类型request/response/heartbeat等, 高地址4位用来表示序列化类型
 * + 1 // 状态位, 设置请求响应状态
 * + 8 // 消息 id, long 类型, 未来jupiter可能将id限制在48位, 留出高地址的16位作为扩展字段
 * + 4 // 消息体 body 长度, int 类型
 * </pre>
 *
 * jupiter
 * org.jupiter.transport.netty.handler
 *
 * @author jiachun.fjc
 */
@ChannelHandler.Sharable
public class LowCopyProtocolEncoder extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = null;
        try {
            if (msg instanceof PayloadHolder) {
                PayloadHolder cast = (PayloadHolder) msg;

                buf = encode(cast);

                ctx.write(buf, promise);

                buf = null;
            } else {
                ctx.write(msg, promise);
            }
        } catch (Throwable t) {
            throw new EncoderException(t);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }

    protected ByteBuf encode(PayloadHolder msg) throws Exception {
        if (msg instanceof RequestPayload) {
            return doEncodeRequest((RequestPayload) msg);
        } else if (msg instanceof ResponsePayload) {
            return doEncodeResponse((ResponsePayload) msg);
        } else {
            throw new IllegalArgumentException(Reflects.simpleClassName(msg));
        }
    }

    private ByteBuf doEncodeRequest(RequestPayload request) {
        byte sign = TransportProtocol.toSign(request.serializerCode(), TransportProtocol.REQUEST);
        long invokeId = request.invokeId();
        ByteBuf byteBuf = (ByteBuf) request.outputBuf().backingObject();
        int length = byteBuf.readableBytes();

        byteBuf.markWriterIndex();

        byteBuf.writerIndex(byteBuf.writerIndex() - length);

        byteBuf.writeShort(TransportProtocol.MAGIC)
                .writeByte(sign)
                .writeByte(0x00)
                .writeLong(invokeId)
                .writeInt(length - TransportProtocol.HEADER_SIZE);

        byteBuf.resetWriterIndex();

        return byteBuf;
    }

    private ByteBuf doEncodeResponse(ResponsePayload response) {
        byte sign = TransportProtocol.toSign(response.serializerCode(), TransportProtocol.RESPONSE);
        byte status = response.status();
        long invokeId = response.id();
        ByteBuf byteBuf = (ByteBuf) response.outputBuf().backingObject();
        int length = byteBuf.readableBytes();

        // 标记当前写索引
        byteBuf.markWriterIndex();

        // 其实是将当前索引归0 在out Bound 中写出的ByteBuf申请的时候就预留了16个头字节长度
        // @see
        // byteBuf.ensureWritable(TransportProtocol.HEADER_SIZE)
        // .writerIndex(byteBuf.writerIndex() + TransportProtocol.HEADER_SIZE);
        // 在这里先标记一下当前的写索引位置，然后将写索引归0，开始写入一些头信息，当写完头信息后再resetWriterIndex，重置回之前的写索引位置
        byteBuf.writerIndex(byteBuf.writerIndex() - length);

        byteBuf.writeShort(TransportProtocol.MAGIC)
                .writeByte(sign)
                .writeByte(status)
                .writeLong(invokeId)
                .writeInt(length - TransportProtocol.HEADER_SIZE);

        byteBuf.resetWriterIndex();

        return byteBuf;
    }


}
