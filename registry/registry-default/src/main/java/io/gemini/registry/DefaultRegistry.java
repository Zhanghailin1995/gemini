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
package io.gemini.registry;

import com.google.common.base.Throwables;
import io.gemini.common.contants.Constants;
import io.gemini.common.util.Signal;
import io.gemini.common.util.*;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.serialization.Serializer;
import io.gemini.serialization.SerializerFactory;
import io.gemini.serialization.SerializerType;
import io.gemini.transport.*;
import io.gemini.transport.exception.ConnectFailedException;
import io.gemini.transport.exception.IoSignals;
import io.gemini.transport.netty.AbstractNettyTcpConnector;
import io.gemini.transport.netty.handler.AcknowledgeEncoder;
import io.gemini.transport.netty.handler.connector.ConnectionWatchdog;
import io.gemini.transport.netty.handler.connector.ConnectorIdleStateTrigger;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.*;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.ConcurrentSet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * The client of registration center.
 * <p>
 * jupiter
 * org.jupiter.registry
 *
 * @author jiachun.fjc
 */
public final class DefaultRegistry extends AbstractNettyTcpConnector {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultRegistry.class);

    private static final AttributeKey<ConcurrentSet<RegisterMeta.ServiceMeta>> C_SUBSCRIBE_KEY =
            AttributeKey.valueOf("client.subscribed");
    private static final AttributeKey<ConcurrentSet<RegisterMeta>> C_PUBLISH_KEY =
            AttributeKey.valueOf("client.published");

    // 没收到对端ack确认, 需要重发的消息
    private static final ConcurrentMap<Long, MessageNonAck> messagesNonAck = MapUtils.newConcurrentMap();

    // 默认超时时间,3秒
    private static final long DEFAULT_TIMEOUT_NANOSECONDS = TimeUnit.MILLISECONDS.toNanos(Constants.DEFAULT_TIMEOUT);

    private static final long TIMEOUT_SCANNER_INTERVAL_MILLIS =
            SystemPropertyUtil.getLong("gemini.rpc.invoke.timeout_scanner_interval_millis", 50);

    private static final HashedWheelTimer timeoutScanner =
            new HashedWheelTimer(
                    new DefaultThreadFactory("ack.timeout.scanner", true),
                    TIMEOUT_SCANNER_INTERVAL_MILLIS, TimeUnit.MILLISECONDS,
                    4096
            );

    // handlers
    private final ConnectorIdleStateTrigger idleStateTrigger = new ConnectorIdleStateTrigger();
    private final MessageHandler handler = new MessageHandler();
    private final MessageEncoder encoder = new MessageEncoder();
    private final AcknowledgeEncoder ackEncoder = new AcknowledgeEncoder();

    // 序列化/反序列化方式
    private final SerializerType serializerType;

    {
        SerializerType expected = SerializerType.parse(SystemPropertyUtil.get("gemini.registry.default.serializer_type"));
        serializerType = expected == null ? SerializerType.getDefault() : expected;
    }

    private final AbstractRegistryService registryService;

    // 每个ConfigClient只保留一个有效channel
    private volatile Channel channel;

    public DefaultRegistry(AbstractRegistryService registryService) {
        this(registryService, 1);
    }

    public DefaultRegistry(AbstractRegistryService registryService, int nWorkers) {
        super(nWorkers);
        this.registryService = Requires.requireNotNull(registryService, "registryService");
    }

    @Override
    protected void doInit() {
        // child options
        config().setOption(Option.SO_REUSEADDR, true);
        config().setOption(Option.CONNECT_TIMEOUT_MILLIS, (int) TimeUnit.SECONDS.toMillis(3));
        // channel factory
        initChannelFactory();
    }

    /**
     * ConfigClient不支持异步连接行为, async参数无效
     */
    @Override
    public Connection connect(UnresolvedAddress address, boolean async) {
        setOptions();

        final Bootstrap boot = bootstrap();
        final SocketAddress socketAddress = InetSocketAddress.createUnresolved(address.getHost(), address.getPort());

        // 重连watchdog
        final ConnectionWatchdog watchdog = new ConnectionWatchdog(boot, timer, socketAddress, null) {

            @Override
            public ChannelHandler[] handlers() {
                return new ChannelHandler[]{
                        this,
                        //new IdleStateChecker(timer, 0, Constants.WRITER_IDLE_TIME_SECONDS, 0),
                        new IdleStateHandler(0, Constants.WRITER_IDLE_TIME_SECONDS, 0, TimeUnit.SECONDS),
                        idleStateTrigger,
                        new MessageDecoder(),
                        encoder,
                        ackEncoder,
                        handler
                };
            }
        };

        try {
            ChannelFuture future;
            synchronized (bootstrap()) {
                boot.handler(new ChannelInitializer<Channel>() {

                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(watchdog.handlers());
                    }
                });

                future = boot.connect(socketAddress);
            }

            // 以下代码在synchronized同步块外面是安全的
            future.sync();
            channel = future.channel();
        } catch (Throwable t) {
            throw new ConnectFailedException("connects to [" + address + "] fails", t);
        }

        return new Connection(address) {

            @Override
            public void setReconnect(boolean reconnect) {
                if (reconnect) {
                    watchdog.start();
                } else {
                    watchdog.stop();
                }
            }
        };
    }

    /**
     * Sent the subscription information to registry server.
     */
    public void doSubscribe(RegisterMeta.ServiceMeta serviceMeta) {
        Message msg = new Message(serializerType.value());
        msg.messageCode(TransportProtocol.SUBSCRIBE_SERVICE);
        msg.data(serviceMeta);

        Channel ch = channel;
        // 与MessageHandler#channelActive()中的write有竞争
        if (attachSubscribeEventOnChannel(serviceMeta, ch)) {
            ch.writeAndFlush(msg)
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

            MessageNonAck msgNonAck = new MessageNonAck(msg);
            messagesNonAck.put(msgNonAck.id, msgNonAck);
            //putMessageNonAck(msgNonAck);
        }
    }

    private void putMessageNonAck(MessageNonAck msgNonAck) {
        if (msgNonAck == null) return;
        messagesNonAck.put(msgNonAck.id, msgNonAck);

        TimeoutTask task = new TimeoutTask(msgNonAck.id, channel);
        timeoutScanner.newTimeout(task, DEFAULT_TIMEOUT_NANOSECONDS, TimeUnit.NANOSECONDS);
    }

    static final class TimeoutTask implements TimerTask {

        private final long msgId;

        private final Channel channel;


        public TimeoutTask(long msgId, Channel channel) {
            this.msgId = msgId;
            this.channel = channel;
        }

        @Override
        public void run(Timeout timeout) throws Exception {
            MessageNonAck msgNonAck = messagesNonAck.remove(msgId);
            if (msgNonAck != null) {
                processTimeout(msgNonAck);
            }

        }

        private void processTimeout(MessageNonAck msgNonAck) {
            if (SystemClock.millisClock().now() - msgNonAck.timestamp > TimeUnit.SECONDS.toMillis(10)) {
                channel.writeAndFlush(msgNonAck.msg)
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            }
        }
    }

    /**
     * Publishing service to registry server.
     */
    public void doRegister(RegisterMeta meta) {
        Message msg = new Message(serializerType.value());
        msg.messageCode(TransportProtocol.PUBLISH_SERVICE);
        msg.data(meta);

        Channel ch = channel;
        // 与MessageHandler#channelActive()中的write有竞争
        if (attachPublishEventOnChannel(meta, ch)) {
            ch.writeAndFlush(msg)
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

            MessageNonAck msgNonAck = new MessageNonAck(msg);
            messagesNonAck.put(msgNonAck.id, msgNonAck);
        }
    }

    /**
     * Notify to registry server unpublish corresponding service.
     */
    public void doUnregister(final RegisterMeta meta) {
        Message msg = new Message(serializerType.value());
        msg.messageCode(TransportProtocol.PUBLISH_CANCEL_SERVICE);
        msg.data(meta);

        channel.writeAndFlush(msg)
                .addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        Channel ch = future.channel();
                        if (ch.isActive()) {
                            ch.pipeline().fireExceptionCaught(future.cause());
                        } else {
                            if (logger.isWarnEnabled()) {
                                logger.warn("Unregister {} fail because of channel is inactive: {}.",
                                        meta, Throwables.getStackTraceAsString(future.cause()));
                            }
                        }
                    }
                });

        MessageNonAck msgNonAck = new MessageNonAck(msg);
        messagesNonAck.put(msgNonAck.id, msgNonAck);
    }

    private void handleAcknowledge(Acknowledge ack) {
        messagesNonAck.remove(ack.sequence());
    }

    // 在channel打标记(发布过的服务)
    private static boolean attachPublishEventOnChannel(RegisterMeta meta, Channel channel) {
        Attribute<ConcurrentSet<RegisterMeta>> attr = channel.attr(C_PUBLISH_KEY);
        ConcurrentSet<RegisterMeta> registerMetaSet = attr.get();
        if (registerMetaSet == null) {
            ConcurrentSet<RegisterMeta> newRegisterMetaSet = new ConcurrentSet<>();
            registerMetaSet = attr.setIfAbsent(newRegisterMetaSet);
            if (registerMetaSet == null) {
                registerMetaSet = newRegisterMetaSet;
            }
        }

        return registerMetaSet.add(meta);
    }

    // 在channel打标记(订阅过的服务)
    private static boolean attachSubscribeEventOnChannel(RegisterMeta.ServiceMeta serviceMeta, Channel channel) {
        Attribute<ConcurrentSet<RegisterMeta.ServiceMeta>> attr = channel.attr(C_SUBSCRIBE_KEY);
        ConcurrentSet<RegisterMeta.ServiceMeta> serviceMetaSet = attr.get();
        if (serviceMetaSet == null) {
            ConcurrentSet<RegisterMeta.ServiceMeta> newServiceMetaSet = new ConcurrentSet<>();
            serviceMetaSet = attr.setIfAbsent(newServiceMetaSet);
            if (serviceMetaSet == null) {
                serviceMetaSet = newServiceMetaSet;
            }
        }

        return serviceMetaSet.add(serviceMeta);
    }

    static class MessageNonAck {
        private final long id;

        private final Message msg;
        private final long timestamp = SystemClock.millisClock().now();

        public MessageNonAck(Message msg) {
            this.msg = msg;
            id = msg.sequence();
        }
    }

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
     * + 1 // 消息标志位, 低地址4位用来表示消息类型, 高地址4位用来表示序列化类型
     * + 1 // 空
     * + 8 // 消息 id, long 类型
     * + 4 // 消息体 body 长度, int 类型
     * </pre>
     */
    static class MessageDecoder extends ReplayingDecoder<MessageDecoder.State> {

        public MessageDecoder() {
            super(State.MAGIC);
        }

        // 协议头
        private final TransportProtocol protocol = new TransportProtocol();

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            switch (state()) {
                case MAGIC:
                    checkMagic(in.readShort());             // MAGIC
                    checkpoint(State.SIGN);
                case SIGN:
                    protocol.sign(in.readByte());             // 消息标志位
                    checkpoint(State.STATUS);
                case STATUS:
                    in.readByte();                          // no-op
                    checkpoint(State.ID);
                case ID:
                    protocol.id(in.readLong());               // 消息id
                    checkpoint(State.BODY_SIZE);
                case BODY_SIZE:
                    protocol.bodySize(in.readInt());          // 消息体长度
                    checkpoint(State.BODY);
                case BODY:
                    byte s_code = protocol.serializerCode();

                    switch (protocol.messageCode()) {
                        case TransportProtocol.PUBLISH_SERVICE:
                        case TransportProtocol.PUBLISH_CANCEL_SERVICE:
                        case TransportProtocol.OFFLINE_NOTICE: {
                            byte[] bytes = new byte[protocol.bodySize()];
                            in.readBytes(bytes);

                            Serializer serializer = SerializerFactory.getSerializer(s_code);
                            Message msg = serializer.readObject(bytes, Message.class);
                            msg.messageCode(protocol.messageCode());
                            out.add(msg);

                            break;
                        }
                        case TransportProtocol.ACK:
                            out.add(new Acknowledge(protocol.id()));

                            break;
                        default:
                            throw IoSignals.ILLEGAL_SIGN;

                    }
                    checkpoint(State.MAGIC);
            }
        }

        private static void checkMagic(short magic) throws Signal {
            if (magic != TransportProtocol.MAGIC) {
                throw IoSignals.ILLEGAL_MAGIC;
            }
        }

        enum State {
            MAGIC,
            SIGN,
            STATUS,
            ID,
            BODY_SIZE,
            BODY
        }
    }

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
     * + 1 // 消息标志位, 低地址4位用来表示消息类型, 高地址4位用来表示序列化类型
     * + 1 // 空
     * + 8 // 消息 id, long 类型
     * + 4 // 消息体 body 长度, int 类型
     * </pre>
     */
    @ChannelHandler.Sharable
    static class MessageEncoder extends MessageToByteEncoder<Message> {

        @Override
        protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
            byte s_code = msg.serializerCode();
            byte sign = TransportProtocol.toSign(s_code, msg.messageCode());
            Serializer serializer = SerializerFactory.getSerializer(s_code);
            byte[] bytes = serializer.writeObject(msg);

            out.writeShort(TransportProtocol.MAGIC)
                    .writeByte(sign)
                    .writeByte(0)
                    .writeLong(0)
                    .writeInt(bytes.length)
                    .writeBytes(bytes);
        }
    }

    @ChannelHandler.Sharable
    class MessageHandler extends ChannelInboundHandlerAdapter {

        @SuppressWarnings("unchecked")
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();

            if (msg instanceof Message) {
                Message obj = (Message) msg;

                switch (obj.messageCode()) {
                    case TransportProtocol.PUBLISH_SERVICE: {
                        Pair<RegisterMeta.ServiceMeta, ?> data = (Pair<RegisterMeta.ServiceMeta, ?>) obj.data();
                        Object metaObj = data.getSecond();

                        if (metaObj instanceof List) {
                            List<RegisterMeta> list = (List<RegisterMeta>) metaObj;
                            RegisterMeta[] array = new RegisterMeta[list.size()];
                            list.toArray(array);
                            registryService.notify(
                                    data.getFirst(),
                                    NotifyListener.NotifyEvent.CHILD_ADDED,
                                    obj.version(),
                                    array
                            );
                        } else if (metaObj instanceof RegisterMeta) {
                            registryService.notify(
                                    data.getFirst(),
                                    NotifyListener.NotifyEvent.CHILD_ADDED,
                                    obj.version(),
                                    (RegisterMeta) metaObj
                            );
                        }

                        ch.writeAndFlush(new Acknowledge(obj.sequence()))  // 回复ACK
                                .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

                        logger.info("Publish from RegistryServer {}, metadata: {}, version: {}.",
                                data.getFirst(), metaObj, obj.version());

                        break;
                    }
                    case TransportProtocol.PUBLISH_CANCEL_SERVICE: {
                        Pair<RegisterMeta.ServiceMeta, RegisterMeta> data =
                                (Pair<RegisterMeta.ServiceMeta, RegisterMeta>) obj.data();
                        registryService.notify(
                                data.getFirst(), NotifyListener.NotifyEvent.CHILD_REMOVED, obj.version(), data.getSecond());

                        ch.writeAndFlush(new Acknowledge(obj.sequence()))  // 回复ACK
                                .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

                        logger.info("Publish cancel from RegistryServer {}, metadata: {}, version: {}.",
                                data.getFirst(), data.getSecond(), obj.version());

                        break;
                    }
                    case TransportProtocol.OFFLINE_NOTICE:
                        RegisterMeta.Address address = (RegisterMeta.Address) obj.data();

                        logger.info("Offline notice on {}.", address);

                        registryService.offline(address);

                        break;
                }
            } else if (msg instanceof Acknowledge) {
                handleAcknowledge((Acknowledge) msg);
            } else {
                logger.warn("Unexpected message type received: {}, channel: {}.", msg.getClass(), ch);

                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Channel ch = (channel = ctx.channel());

            // 重新订阅
            for (RegisterMeta.ServiceMeta serviceMeta : registryService.getSubscribeSet()) {
                // 与doSubscribe()中的write有竞争
                if (!attachSubscribeEventOnChannel(serviceMeta, ch)) {
                    continue;
                }

                Message msg = new Message(serializerType.value());
                msg.messageCode(TransportProtocol.SUBSCRIBE_SERVICE);
                msg.data(serviceMeta);

                ch.writeAndFlush(msg)
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

                MessageNonAck msgNonAck = new MessageNonAck(msg);
                messagesNonAck.put(msgNonAck.id, msgNonAck);
            }

            // 重新发布服务
            for (RegisterMeta meta : registryService.getRegisterMetaMap().keySet()) {
                // 与doRegister()中的write有竞争
                if (!attachPublishEventOnChannel(meta, ch)) {
                    continue;
                }

                Message msg = new Message(serializerType.value());
                msg.messageCode(TransportProtocol.PUBLISH_SERVICE);
                msg.data(meta);

                ch.writeAndFlush(msg)
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

                MessageNonAck msgNonAck = new MessageNonAck(msg);
                messagesNonAck.put(msgNonAck.id, msgNonAck);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            Channel ch = ctx.channel();

            if (cause instanceof Signal) {
                logger.error("I/O signal was caught: {}, force to close channel: {}.", ((Signal) cause).name(), ch);

                ch.close();
            } else if (cause instanceof IOException) {
                logger.error("I/O exception was caught: {}, force to close channel: {}.",
                        Throwables.getStackTraceAsString(cause), ch);

                ch.close();
            } else if (cause instanceof DecoderException) {
                logger.error("Decoder exception was caught: {}, force to close channel: {}.",
                        Throwables.getStackTraceAsString(cause), ch);

                ch.close();
            } else {
                logger.error("Unexpected exception was caught: {}, channel: {}.", Throwables.getStackTraceAsString(cause), ch);
            }
        }
    }

    private class AckTimeoutScanner implements Runnable {

        @SuppressWarnings("all")
        @Override
        public void run() {
            for (; ; ) {
                try {
                    for (MessageNonAck m : messagesNonAck.values()) {
                        if (SystemClock.millisClock().now() - m.timestamp > TimeUnit.SECONDS.toMillis(10)) {

                            // 移除
                            if (messagesNonAck.remove(m.id) == null) {
                                continue;
                            }
                            // 有任务超时了没有回复
                            MessageNonAck msgNonAck = new MessageNonAck(m.msg);
                            messagesNonAck.put(msgNonAck.id, msgNonAck);
                            channel.writeAndFlush(m.msg)
                                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                        }
                    }

                    Thread.sleep(300);
                } catch (Throwable t) {
                    logger.error("An exception was caught while scanning the timeout acknowledges {}.",
                            Throwables.getStackTraceAsString(t));
                }
            }
        }
    }

    {
        Thread t = new Thread(new AckTimeoutScanner(), "ack.timeout.scanner");
        t.setDaemon(true);
        t.start();
    }
}
