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
package io.gemini.transport.netty.channel;

import io.gemini.serialization.io.OutputBuf;
import io.gemini.transport.TransportProtocol;
import io.gemini.transport.channel.Chan;
import io.gemini.transport.channel.FutureListener;
import io.gemini.transport.netty.alloc.AdaptiveOutputBufAllocator;
import io.gemini.transport.netty.handler.connector.ConnectionWatchdog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.internal.PlatformDependent;

import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Queue;

/**
 * 对Netty {@link Channel} 的包装, 通过静态方法 {@link #attachChannel(Channel)} 获取一个实例,
 * {@link NettyChan} 实例构造后会attach到对应 {@link Channel} 上, 不需要每次创建.
 * <p>
 * jupiter
 * org.jupiter.transport.netty.channel
 *
 * @author jiachun.fjc
 */
public class NettyChan implements Chan {

    private static final AttributeKey<NettyChan> NETTY_CHANNEL_KEY = AttributeKey.valueOf("netty.channel");

    /**
     * Returns the {@link NettyChan} for given {@link ChannelHandlerContext}, this method never return null.
     */
    public static NettyChan attachChannel(Channel channel) {
        Attribute<NettyChan> attr = channel.attr(NETTY_CHANNEL_KEY);
        NettyChan nChannel = attr.get();
        if (nChannel == null) {
            NettyChan newNChannel = new NettyChan(channel);
            nChannel = attr.setIfAbsent(newNChannel);
            if (nChannel == null) {
                nChannel = newNChannel;
            }
        }
        return nChannel;
    }


    private final Channel channel;
    private final AdaptiveOutputBufAllocator.Handle allocHandle = AdaptiveOutputBufAllocator.DEFAULT.newHandle();

    private final Queue<Runnable> taskQueue = PlatformDependent.newMpscQueue(1024);
    private final Runnable runAllTasks = this::runAllTasks;

    private NettyChan(Channel channel) {
        this.channel = channel;
    }

    public Channel channel() {
        return this.channel;
    }

    @Override
    public String id() {
        return channel.id().asShortText(); // 注意这里的id并不是全局唯一, 单节点中是唯一的
    }

    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    @Override
    public boolean inIoThread() {
        return channel.eventLoop().inEventLoop();
    }

    @Override
    public SocketAddress localAddress() {
        return channel.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public boolean isWritable() {
        return channel.isWritable();
    }

    @Override
    public boolean isMarkedReconnect() {
        ConnectionWatchdog watchdog = channel.pipeline().get(ConnectionWatchdog.class);
        return watchdog != null && watchdog.isStarted();
    }

    @Override
    public boolean isAutoRead() {
        return channel.config().isAutoRead();
    }

    @Override
    public void setAutoRead(boolean autoRead) {
        channel.config().setAutoRead(autoRead);
    }

    @Override
    public Chan close() {
        channel.close();
        return this;
    }

    @Override
    public Chan close(FutureListener<Chan> listener) {
        final Chan chan = this;
        channel.close().addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                listener.operationSuccess(chan);
            } else {
                listener.operationFailure(chan, future.cause());
            }
        });
        return chan;
    }

    @Override
    public Chan write(Object msg) {
        channel.writeAndFlush(msg, channel.voidPromise());
        return this;
    }

    @Override
    public Chan write(Object msg, FutureListener<Chan> listener) {
        final Chan chan = this;
        channel.writeAndFlush(msg)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        listener.operationSuccess(chan);
                    } else {
                        listener.operationFailure(chan, future.cause());
                    }
                });
        return chan;
    }

    @Override
    public void addTask(Runnable task) {
        EventLoop eventLoop = channel.eventLoop();

        while (!taskQueue.offer(task)) {
            if (eventLoop.inEventLoop()) {
                runAllTasks.run();
            } else {
                // TODO await?
                eventLoop.execute(runAllTasks);
            }
        }

        if (!taskQueue.isEmpty()) {
            eventLoop.execute(runAllTasks);
        }
    }

    @Override
    public OutputBuf allocOutputBuf() {
        return new NettyOutputBuf(allocHandle, channel.alloc());
    }

    private void runAllTasks() {
        if (taskQueue.isEmpty()) {
            return;
        }

        for (;;) {
            Runnable task = taskQueue.poll();
            if (task == null) {
                return;
            }
            task.run();
        }
    }

    static final class NettyOutputBuf implements OutputBuf {

        private final AdaptiveOutputBufAllocator.Handle allocHandle;
        private final ByteBuf byteBuf;
        private ByteBuffer nioByteBuffer;

        public NettyOutputBuf(AdaptiveOutputBufAllocator.Handle allocHandle, ByteBufAllocator alloc) {
            this.allocHandle = allocHandle;
            byteBuf = allocHandle.allocate(alloc);

            byteBuf.ensureWritable(TransportProtocol.HEADER_SIZE)
                    // reserved 16-byte protocol header location
                    .writerIndex(byteBuf.writerIndex() + TransportProtocol.HEADER_SIZE);
        }

        @Override
        public OutputStream outputStream() {
            return new ByteBufOutputStream(byteBuf); // should not be called more than once
        }

        @Override
        public ByteBuffer nioByteBuffer(int minWritableBytes) {
            if (minWritableBytes < 0) {
                minWritableBytes = byteBuf.writableBytes();
            }

            if (nioByteBuffer == null) {
                nioByteBuffer = newNioByteBuffer(byteBuf, minWritableBytes);
            }

            if (nioByteBuffer.remaining() >= minWritableBytes) {
                return nioByteBuffer;
            }

            int position = nioByteBuffer.position();
            nioByteBuffer = newNioByteBuffer(byteBuf, position + minWritableBytes);
            nioByteBuffer.position(position);
            return nioByteBuffer;
        }

        @Override
        public int size() {
            if (nioByteBuffer == null) {
                return byteBuf.readableBytes();
            }
            return Math.max(byteBuf.readableBytes(), nioByteBuffer.position());
        }

        @Override
        public boolean hasMemoryAddress() {
            return byteBuf.hasMemoryAddress();
        }

        @Override
        public Object backingObject() {
            int actualWroteBytes = byteBuf.writerIndex();
            if (nioByteBuffer != null) {
                actualWroteBytes += nioByteBuffer.position();
            }

            allocHandle.record(actualWroteBytes);

            return byteBuf.writerIndex(actualWroteBytes);
        }

        private static ByteBuffer newNioByteBuffer(ByteBuf byteBuf, int writableBytes) {
            return byteBuf
                    .ensureWritable(writableBytes)
                    .nioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());
        }
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof NettyChan && channel.equals(((NettyChan) obj).channel));
    }

    @Override
    public int hashCode() {
        return channel.hashCode();
    }

    @Override
    public String toString() {
        return channel.toString();
    }
}
