package io.gemini.transport;

import io.gemini.transport.channel.JChannelGroup;

/**
 * gemini
 * io.gemini.transport.JConnector
 *
 * @author zhanghailin
 */
public interface JConnector<C> {

    /**
     * Connector options [parent, child].
     */
    JConfig config();

    /**
     * Connects to the remote peer.
     */
    C connect(UnresolvedAddress address);

    /**
     * Connects to the remote peer.
     */
    C connect(UnresolvedAddress address, boolean async);

    /**
     * Returns or new a {@link JChannelGroup}.
     */
    JChannelGroup group(UnresolvedAddress address);

    /**
     * Shutdown the server.
     */
    void shutdownGracefully();

    interface ConnectionWatcher {

        /**
         * Start to connect to server.
         */
        void start();

        /**
         * Wait until the connections is available or timeout,
         * if available return true, otherwise return false.
         */
        boolean waitForAvailable(long timeoutMillis);
    }
}
