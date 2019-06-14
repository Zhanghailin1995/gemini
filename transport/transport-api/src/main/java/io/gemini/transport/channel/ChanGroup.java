package io.gemini.transport.channel;

import io.gemini.transport.Directory;
import io.gemini.transport.UnresolvedAddress;

import java.util.List;

/**
 * Based on the same address of the channel group.
 *
 * 要注意的是它管理的是相同地址的 {@link Chan}.
 *
 * jupiter
 * org.jupiter.transport.channel
 *
 * @author jiachun.fjc
 */
public interface ChanGroup {

    /**
     * Returns the remote address of this group.
     */
    UnresolvedAddress remoteAddress();

    /**
     * Returns the next {@link Chan} in the group.
     */
    Chan next();

    /**
     * Returns all {@link Chan}s in the group.
     */
    List<? extends Chan> channels();

    /**
     * Returns true if this group contains no {@link Chan}.
     */
    boolean isEmpty();

    /**
     * Adds the specified {@link Chan} to this group.
     */
    boolean add(Chan channel);

    /**
     * Removes the specified {@link Chan} from this group.
     */
    boolean remove(Chan channel);

    /**
     * Returns the number of {@link Chan}s in this group (its cardinality).
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
     * Wait until the {@link Chan}s are available or timeout,
     * if available return true, otherwise return false.
     */
    boolean waitForAvailable(long timeoutMillis);

    /**
     * Be called when the {@link Chan}s are available.
     */
    void onAvailable(Runnable listener);

    /**
     * Gets weight of service.
     */
    int getWeight(Directory directory);

    /**
     * Puts the weight of service.
     */
    void putWeight(Directory directory, int weight);

    /**
     * Removes the weight of service.
     */
    void removeWeight(Directory directory);

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
