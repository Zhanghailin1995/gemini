package io.gemini.transport.channel;

import java.net.SocketAddress;
import java.util.List;

/**
 * Based on the same address of the channel group.
 *
 * 要注意的是它管理的是相同地址的 {@link JChannel}.
 *
 * jupiter
 * org.jupiter.transport.channel
 *
 * @author jiachun.fjc
 */
public interface JChannelGroup {

    /**
     * Returns the remote address of this group.
     */
    SocketAddress remoteAddress();

    /**
     * Returns the next {@link JChannel} in the group.
     */
    JChannel next();

    /**
     * Returns all {@link JChannel}s in the group.
     */
    List<? extends JChannel> channels();

    /**
     * Returns true if this group contains no {@link JChannel}.
     */
    boolean isEmpty();

    /**
     * Adds the specified {@link JChannel} to this group.
     */
    boolean add(JChannel channel);

    /**
     * Removes the specified {@link JChannel} from this group.
     */
    boolean remove(JChannel channel);

    /**
     * Returns the number of {@link JChannel}s in this group (its cardinality).
     */
    int size();

    /**
     * Sets the capacity of this group.
     */
    void setCapacity(int capacity);

    /**
     * The capacity of this group.
     */
    int getCapacity();

    /**
     * If connecting return true, otherwise return false.
     */
    boolean isConnecting();

    /**
     * Sets connecting state
     */
    void setConnecting(boolean connecting);

    /**
     * If available return true, otherwise return false.
     */
    boolean isAvailable();

    /**
     * Wait until the {@link JChannel}s are available or timeout,
     * if available return true, otherwise return false.
     */
    boolean waitForAvailable(long timeoutMillis);

    /**
     * Be called when the {@link JChannel}s are available.
     */
    void onAvailable(Runnable listener);

    /**
     * Warm-up time.
     */
    int getWarmUp();

    /**
     * Sets warm-up time.
     */
    void setWarmUp(int warmUp);

    /**
     * Returns {@code true} if warm up to complete.
     */
    boolean isWarmUpComplete();

    /**
     * Time of birth.
     */
    long timestamp();

    /**
     * Deadline millis.
     */
    long deadlineMillis();
}
