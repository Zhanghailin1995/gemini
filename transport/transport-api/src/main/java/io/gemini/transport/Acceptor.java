package io.gemini.transport;

import io.gemini.transport.processor.ProviderProcessor;

import java.net.SocketAddress;

/**
 * gemini
 * io.gemini.transport.Acceptor
 *
 * @author zhanghailin
 */
public interface Acceptor extends Transporter {

    /**
     * Local address.
     */
    SocketAddress localAddress();

    /**
     * Returns bound port.
     */
    int boundPort();

    /**
     * Acceptor options [parent, child].
     */
    ConfigGroup configGroup();

    /**
     * Returns the rpc processor.
     */
    ProviderProcessor processor();

    /**
     * Binds the rpc processor.
     */
    void withProcessor(ProviderProcessor processor);


    /**
     * Start the server and wait until the server socket is closed.
     */
    void start() throws InterruptedException;

    /**
     * Start the server.
     */
    void start(boolean sync) throws InterruptedException;

    /**
     * Shutdown the server gracefully.
     */
    void shutdownGracefully();
}
