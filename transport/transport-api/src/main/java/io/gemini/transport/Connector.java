package io.gemini.transport;

import io.gemini.transport.channel.ChanGroup;
import io.gemini.transport.channel.CopyOnWriteGroupList;
import io.gemini.transport.channel.DirectoryChanGroup;
import io.gemini.transport.processor.ConsumerProcessor;

import java.util.Collection;

/**
 * gemini
 * io.gemini.transport.Connector
 *
 * @author zhanghailin
 */
public interface Connector<C> extends Transporter {

    /**
     * Connector options [parent, child].
     */
    Config config();

    /**
     * Returns the rpc processor.
     */
    ConsumerProcessor processor();

    /**
     * Binds the rpc processor.
     */
    void withProcessor(ConsumerProcessor processor);

    /**
     * Connects to the remote peer.
     */
    C connect(UnresolvedAddress address);

    /**
     * Connects to the remote peer.
     */
    C connect(UnresolvedAddress address, boolean async);

    /**
     * Returns or new a {@link ChanGroup}.
     */
    ChanGroup group(UnresolvedAddress address);

    /**
     * Returns all {@link ChanGroup}s.
     */
    Collection<ChanGroup> groups();

    /**
     * Adds a {@link ChanGroup} by {@link Directory}.
     */
    boolean addChannelGroup(Directory directory, ChanGroup group);

    /**
     * Removes a {@link ChanGroup} by {@link Directory}.
     */
    boolean removeChannelGroup(Directory directory, ChanGroup group);

    /**
     * Returns list of {@link ChanGroup}s by the same {@link Directory}.
     */
    CopyOnWriteGroupList directory(Directory directory);

    /**
     * Returns {@code true} if has available {@link ChanGroup}s
     * on this {@link Directory}.
     */
    boolean isDirectoryAvailable(Directory directory);

    /**
     * Returns the {@link DirectoryChanGroup}.
     */
    DirectoryChanGroup directoryGroup();

    /**
     * Returns the {@link ConnectionManager}.
     */
    ConnectionManager connectionManager();

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
