package io.gemini.transport.netty;

import io.gemini.common.contants.Constants;
import io.gemini.common.util.Requires;
import io.gemini.transport.CodecConfig;
import io.gemini.transport.Config;
import io.gemini.transport.Option;
import io.gemini.transport.netty.handler.LowCopyProtocolDecoder;
import io.gemini.transport.netty.handler.LowCopyProtocolEncoder;
import io.gemini.transport.netty.handler.ProtocolDecoder;
import io.gemini.transport.netty.handler.ProtocolEncoder;
import io.gemini.transport.netty.handler.acceptor.AcceptorHandler;
import io.gemini.transport.netty.handler.acceptor.AcceptorIdleStateTrigger;
import io.gemini.transport.processor.ProviderProcessor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.handler.flush.FlushConsolidationHandler;
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
public class SimpleNettyTcpAcceptor extends AbstractNettyTcpAcceptor {

    public static final int DEFAULT_ACCEPTOR_PORT = 18090;

    // handlers
    private final AcceptorIdleStateTrigger idleStateTrigger = new AcceptorIdleStateTrigger();
    private final ChannelOutboundHandler encoder =
            CodecConfig.isCodecLowCopy() ? new LowCopyProtocolEncoder() : new ProtocolEncoder();
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
        Config parent = configGroup().parent();
        parent.setOption(Option.SO_BACKLOG, 32768);
        parent.setOption(Option.SO_REUSEADDR, true);

        // child options
        Config child = configGroup().child();
        child.setOption(Option.SO_REUSEADDR, true);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        ServerBootstrap boot = bootstrap();

        initChannelFactory();

        boot.childHandler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(
                        new FlushConsolidationHandler(Constants.EXPLICIT_FLUSH_AFTER_FLUSHES, true),
                        new IdleStateHandler(Constants.READER_IDLE_TIME_SECONDS, 0, 0, TimeUnit.SECONDS),
                        idleStateTrigger,
                        CodecConfig.isCodecLowCopy() ? new LowCopyProtocolDecoder() : new ProtocolDecoder(),
                        encoder,
                        handler);
            }
        });

        setOptions();

        return boot.bind(localAddress);
    }

    @Override
    protected void setProcessor(ProviderProcessor processor) {
        handler.processor(Requires.requireNotNull(processor, "processor"));
    }

}
