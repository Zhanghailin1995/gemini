package io.gemini.transport;

import io.gemini.transport.processor.MessageProcessor;

import java.net.SocketAddress;

/**
 * gemini
 * io.gemini.transport.JAcceptor
 *
 * @author zhanghailin
 */
public interface JAcceptor extends Transpoter {

    /**
     * Local address.
     */
    SocketAddress localAddress();

    /**
     * Acceptor options [parent, child].
     */
    JConfigGroup configGroup();

    /**
     * Returns the msg processor.
     */
    MessageProcessor processor();

    /**
     * Binds the msg processor.
     */
    void withProcessor(MessageProcessor processor);

    /**
     * Returns bound port.
     */
    int boundPort();

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
