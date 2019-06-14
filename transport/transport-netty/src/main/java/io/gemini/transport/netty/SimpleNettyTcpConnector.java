package io.gemini.transport.netty;

import io.gemini.common.contants.Constants;
import io.gemini.common.util.Requires;
import io.gemini.transport.CodecConfig;
import io.gemini.transport.Connection;
import io.gemini.transport.Option;
import io.gemini.transport.UnresolvedAddress;
import io.gemini.transport.channel.ChanGroup;
import io.gemini.transport.exception.ConnectFailedException;
import io.gemini.transport.netty.handler.LowCopyProtocolDecoder;
import io.gemini.transport.netty.handler.LowCopyProtocolEncoder;
import io.gemini.transport.netty.handler.ProtocolDecoder;
import io.gemini.transport.netty.handler.ProtocolEncoder;
import io.gemini.transport.netty.handler.connector.ConnectionWatchdog;
import io.gemini.transport.netty.handler.connector.ConnectorHandler;
import io.gemini.transport.netty.handler.connector.ConnectorIdleStateTrigger;
import io.gemini.transport.processor.ConsumerProcessor;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Jupiter tcp connector based on netty.
 *
 * <pre>
 * ************************************************************************
 *                      ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
 *
 *                 ─ ─ ─│        Server         │─ ─▷
 *                 │                                 │
 *                      └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
 *                 │                                 ▽
 *                                              I/O Response
 *                 │                                 │
 *
 *                 │                                 │
 * ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
 * │               │                                 │                │
 *
 * │               │                                 │                │
 *   ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐      ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ─
 * │  ConnectionWatchdog#outbound        ConnectionWatchdog#inbound│  │
 *   └ ─ ─ ─ ─ ─ ─ △ ─ ─ ─ ─ ─ ─ ┘      └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
 * │                                                 │                │
 *                 │
 * │  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐       ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐   │
 *     IdleStateChecker#outBound         IdleStateChecker#inBound
 * │  └ ─ ─ ─ ─ ─ ─△─ ─ ─ ─ ─ ─ ┘       └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘   │
 *                                                   │
 * │               │                                                  │
 *                                      ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐
 * │               │                     ConnectorIdleStateTrigger    │
 *                                      └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
 * │               │                                 │                │
 *
 * │               │                    ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐   │
 *                                            ProtocolDecoder
 * │               │                    └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘   │
 *                                                   │
 * │               │                                                  │
 *                                      ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐
 * │               │                         ConnectorHandler         │
 *    ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐       └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
 * │        ProtocolEncoder                          │                │
 *    └ ─ ─ ─ ─ ─ ─△─ ─ ─ ─ ─ ─ ┘
 * │                                                 │                │
 * ─ ─ ─ ─ ─ ─ ─ ─ ┼ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
 *                                       ┌ ─ ─ ─ ─ ─ ▽ ─ ─ ─ ─ ─ ┐
 *                 │
 *                                       │       Processor       │
 *                 │
 *            I/O Request                └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
 *
 * </pre>
 *
 * jupiter
 * org.jupiter.transport.netty
 *
 * @author jiachun.fjc
 */
public class SimpleNettyTcpConnector extends AbstractNettyTcpConnector {

    // handlers
    private final ConnectorIdleStateTrigger idleStateTrigger = new ConnectorIdleStateTrigger();
    private final ChannelOutboundHandler encoder =
            CodecConfig.isCodecLowCopy() ? new LowCopyProtocolEncoder() : new ProtocolEncoder();
    private final ConnectorHandler handler = new ConnectorHandler();

    public SimpleNettyTcpConnector() {
        super();
    }

    public SimpleNettyTcpConnector(boolean isNative) {
        super(isNative);
    }

    public SimpleNettyTcpConnector(int nWorkers) {
        super(nWorkers);
    }

    public SimpleNettyTcpConnector(int nWorkers, boolean isNative) {
        super(nWorkers, isNative);
    }


    @Override
    protected void doInit() {
        // child options
        config().setOption(Option.SO_REUSEADDR, true);
        config().setOption(Option.CONNECT_TIMEOUT_MILLIS, (int) TimeUnit.SECONDS.toMillis(3));
        // channel factory
        initChannelFactory();
    }

    @Override
    protected void setProcessor(ConsumerProcessor processor) {
        handler.processor(Requires.requireNotNull(processor, "processor"));
    }


    @Override
    public Connection connect(UnresolvedAddress address, boolean async) {
        setOptions();

        final Bootstrap boot = bootstrap();
        final SocketAddress socketAddress = InetSocketAddress.createUnresolved(address.getHost(), address.getPort());
        final ChanGroup group = group(address);

        // 重连watchdog
        final ConnectionWatchdog watchdog = new ConnectionWatchdog(boot, timer, socketAddress, group) {

            @Override
            public ChannelHandler[] handlers() {
                return new ChannelHandler[] {
                        new FlushConsolidationHandler(Constants.EXPLICIT_FLUSH_AFTER_FLUSHES, true),
                        this,
                        new IdleStateHandler(0, Constants.WRITER_IDLE_TIME_SECONDS, 0, TimeUnit.SECONDS),
                        idleStateTrigger,
                        CodecConfig.isCodecLowCopy() ? new LowCopyProtocolDecoder() : new ProtocolDecoder(),
                        encoder,
                        handler
                };
            }
        };

        ChannelFuture future;
        try {
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
            if (!async) {
                future.sync();
            }
        } catch (Throwable t) {
            throw new ConnectFailedException("Connects to [" + address + "] fails", t);
        }

        return new NettyConnection(address, future) {

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
}
