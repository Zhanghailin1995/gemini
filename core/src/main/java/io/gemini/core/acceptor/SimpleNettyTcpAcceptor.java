package io.gemini.core.acceptor;

import io.gemini.common.util.Requires;
import io.gemini.transport.JConfig;
import io.gemini.transport.JOption;
import io.gemini.transport.netty.NettyTcpAcceptor;
import io.gemini.transport.netty.handler.AcceptorHandler;
import io.gemini.transport.netty.handler.AcceptorIdleStateTrigger;
import io.gemini.transport.netty.handler.LowCopyProtocolDecoder;
import io.gemini.transport.netty.handler.LowCopyProtocolEncoder;
import io.gemini.transport.processor.MessageProcessor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * gemini tcp acceptor based on netty.
 *
 * <pre>
 * *********************************************************************
 *            I/O Request                       I/O Response
 *                 │                                 △
 *                                                   │
 *                 │
 * ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┼ ─ ─ ─ ─ ─ ─ ─ ─
 * │               │                                                  │
 *                                                   │
 * │  ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐       ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐   │
 *     IdleStateChecker#inBound          IdleStateChecker#outBound
 * │  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘       └ ─ ─ ─ ─ ─ ─△─ ─ ─ ─ ─ ─ ┘   │
 *                 │                                 │
 * │                                                                  │
 *                 │                                 │
 * │  ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐                                     │
 *     AcceptorIdleStateTrigger                      │
 * │  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘                                     │
 *                 │                                 │
 * │                                                                  │
 *                 │                                 │
 * │  ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐       ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐   │
 *          ProtocolDecoder                   ProtocolEncoder
 * │  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘       └ ─ ─ ─ ─ ─ ─△─ ─ ─ ─ ─ ─ ┘   │
 *                 │                                 │
 * │                                                                  │
 *                 │                                 │
 * │  ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐                                     │
 *          AcceptorHandler                          │
 * │  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘                                     │
 *                 │                                 │
 * │                    ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐                     │
 *                 ▽                                 │
 * │               ─ ─ ▷│       Processor       ├ ─ ─▷                │
 *
 * │                    └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘                     │
 * ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
 * gemini
 * io.gemini.transport.netty.SimpleNettyTcpAcceptor
 *
 * @author zhanghailin
 */
public class SimpleNettyTcpAcceptor extends NettyTcpAcceptor {

    public static final int DEFAULT_ACCEPTOR_PORT = 18090;

    // handlers
    private final AcceptorIdleStateTrigger idleStateTrigger = new AcceptorIdleStateTrigger();
    private final ChannelOutboundHandler encoder = new LowCopyProtocolEncoder();
    private final AcceptorHandler handler = new AcceptorHandler();

    public SimpleNettyTcpAcceptor() {
        super(DEFAULT_ACCEPTOR_PORT);
    }

    public SimpleNettyTcpAcceptor(int port) {
        super(port);
    }

    public SimpleNettyTcpAcceptor(SocketAddress localAddress) {
        super(localAddress);
    }

    public SimpleNettyTcpAcceptor(int port, int nWorkers) {
        super(port, nWorkers);
    }

    public SimpleNettyTcpAcceptor(SocketAddress localAddress, int nWorkers) {
        super(localAddress, nWorkers);
    }

    public SimpleNettyTcpAcceptor(int port, boolean isNative) {
        super(port, isNative);
    }

    public SimpleNettyTcpAcceptor(SocketAddress localAddress, boolean isNative) {
        super(localAddress, isNative);
    }

    public SimpleNettyTcpAcceptor(int port, int nWorkers, boolean isNative) {
        super(port, nWorkers, isNative);
    }

    public SimpleNettyTcpAcceptor(SocketAddress localAddress, int nWorkers, boolean isNative) {
        super(localAddress, nWorkers, isNative);
    }


    @Override
    protected void init() {
        super.init();

        // parent options
        JConfig parent = configGroup().parent();
        parent.setOption(JOption.SO_BACKLOG, 32768);
        parent.setOption(JOption.SO_REUSEADDR, true);

        // child options
        JConfig child = configGroup().child();
        child.setOption(JOption.SO_REUSEADDR, true);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        ServerBootstrap boot = bootstrap();

        initChannelFactory();

        boot.childHandler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(
                        new IdleStateHandler(45, 0, 0, TimeUnit.SECONDS),
                        idleStateTrigger,
                        new LowCopyProtocolDecoder(),
                        encoder,
                        handler);
            }
        });

        setOptions();

        return boot.bind(localAddress);
    }

    @Override
    public void setProcessor(MessageProcessor processor) {
        handler.processor(Requires.requireNotNull(processor, "processor"));
    }

}
