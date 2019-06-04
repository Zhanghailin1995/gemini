package io.gemini.transport.channel;

import java.net.SocketAddress;

/**
 *
 * gemini
 * io.gemini.transport.channel.JChannel
 *
 * @author zhanghailin
 */
public interface JChannel {

    /**
     * Returns the identifier of this {@link JChannel}.
     */
    String id();

    /**
     * Return {@code true} if the {@link JChannel} is active and so connected.
     */
    boolean isActive();

    /**
     * Returns the local address where this channel is bound to.
     */
    SocketAddress localAddress();

    /**
     * Returns the remote address where this channel is connected to.
     */
    SocketAddress remoteAddress();

    /**
     * Requests to close this {@link JChannel}.
     */
    JChannel close();


    /**
     * Requests to write a message on the channel.
     */
    JChannel write(Object msg);
}
