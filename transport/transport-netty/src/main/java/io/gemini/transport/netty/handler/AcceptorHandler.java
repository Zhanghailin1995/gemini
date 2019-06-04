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

import com.google.common.base.Throwables;
import io.gemini.common.util.Signal;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.transport.netty.channel.NettyChannel;
import io.gemini.transport.payload.JMessagePayload;
import io.gemini.transport.processor.MessageProcessor;
import io.netty.channel.*;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * jupiter
 * org.jupiter.transport.netty.handler.acceptor
 *
 * @author jiachun.fjc
 */
@ChannelHandler.Sharable
public class AcceptorHandler extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AcceptorHandler.class);


    private static final AtomicInteger channelCounter = new AtomicInteger(0);

    private MessageProcessor processor;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof JMessagePayload) {
            NettyChannel channel = NettyChannel.attachChannel(ctx);
            try {
                // 业务委托给processor执行
                processor.handleMessage(channel, (JMessagePayload) msg);
            } catch (Throwable t) {
                processor.handleException(channel, (JMessagePayload) msg, t);
            }
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        int count = channelCounter.incrementAndGet();

        logger.info("Connects with {} as the {}th channel.", ctx.channel(), count);

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        int count = channelCounter.getAndDecrement();

        logger.warn("Disconnects with {} as the {}th channel.", ctx.channel(), count);

        super.channelInactive(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        ChannelConfig config = ch.config();

        // 高水位线: ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK
        // 低水位线: ChannelOption.WRITE_BUFFER_LOW_WATER_MARK
        if (!ch.isWritable()) {
            // 当前channel的缓冲区(OutboundBuffer)大小超过了WRITE_BUFFER_HIGH_WATER_MARK
            if (logger.isWarnEnabled()) {
                logger.warn("{} is not writable, high water mask: {}, the number of flushed entries that are not written yet: {}.",
                        ch, config.getWriteBufferHighWaterMark(), ch.unsafe().outboundBuffer().size());
            }

            config.setAutoRead(false);
        } else {
            // 曾经高于高水位线的OutboundBuffer现在已经低于WRITE_BUFFER_LOW_WATER_MARK了
            if (logger.isWarnEnabled()) {
                logger.warn("{} is writable(rehabilitate), low water mask: {}, the number of flushed entries that are not written yet: {}.",
                        ch, config.getWriteBufferLowWaterMark(), ch.unsafe().outboundBuffer().size());
            }

            config.setAutoRead(true);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel ch = ctx.channel();

        if (cause instanceof Signal) {
            logger.error("I/O signal was caught: {}, force to close channel: {}.", ((Signal) cause).name(), ch);

            ch.close();
        } else if (cause instanceof IOException) {
            logger.error("An I/O exception was caught: {}, force to close channel: {}.", Throwables.getStackTraceAsString(cause), ch);

            ch.close();
        } else if (cause instanceof DecoderException) {
            logger.error("Decoder exception was caught: {}, force to close channel: {}.", Throwables.getStackTraceAsString(cause), ch);

            ch.close();
        } else {
            logger.error("Unexpected exception was caught: {}, channel: {}.", Throwables.getStackTraceAsString(cause), ch);
        }
    }

    public MessageProcessor processor() {
        return processor;
    }

    public void processor(MessageProcessor processor) {
        this.processor = processor;
    }
}
