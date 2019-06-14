package io.gemini.transport.channel;

import java.net.SocketAddress;

/**
 *
 * gemini
 * io.gemini.transport.channel.Chan
 *
 * @author zhanghailin
 */
public interface Chan {

    /**
     * Returns the identifier of this {@link Chan}.
     */
    String id();

    /**
     * Return {@code true} if the {@link Chan} is active and so connected.
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
     * Requests to close this {@link Chan}.
     */
    Chan close();


    /**
     * Requests to write a message on the channel.
     */
    Chan write(Object msg);
}
