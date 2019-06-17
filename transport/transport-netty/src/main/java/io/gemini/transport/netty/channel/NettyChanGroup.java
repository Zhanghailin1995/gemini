/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gemini.transport.netty.channel;

import com.google.common.collect.Lists;
import io.gemini.common.atomic.AtomicUpdater;
import io.gemini.common.contants.Constants;
import io.gemini.common.util.*;
import io.gemini.transport.Directory;
import io.gemini.transport.UnresolvedAddress;
import io.gemini.transport.channel.Chan;
import io.gemini.transport.channel.ChanGroup;
import io.netty.channel.ChannelFutureListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * jupiter
 * org.jupiter.transport.netty.channel
 *
 * @author jiachun.fjc
 */
public class NettyChanGroup implements ChanGroup {

    private static long LOSS_INTERVAL = SystemPropertyUtil
            .getLong("gemini.io.channel.group.loss.interval.millis", TimeUnit.MINUTES.toMillis(5));

    private static int DEFAULT_SEQUENCE_STEP = (Constants.AVAILABLE_PROCESSORS << 3) + 1;

    private static final AtomicReferenceFieldUpdater<CopyOnWriteArrayList, Object[]> channelsUpdater =
            AtomicUpdater.newAtomicReferenceFieldUpdater(CopyOnWriteArrayList.class, Object[].class, "array");
    private static final AtomicIntegerFieldUpdater<NettyChanGroup> signalNeededUpdater =
            AtomicIntegerFieldUpdater.newUpdater(NettyChanGroup.class, "signalNeeded");

    private final ConcurrentLinkedQueue<Runnable> waitAvailableListeners = new ConcurrentLinkedQueue<>();

    private final UnresolvedAddress address;

    private final CopyOnWriteArrayList<NettyChan> channels = new CopyOnWriteArrayList<>();

    // 连接断开时自动被移除
    private final ChannelFutureListener remover = future -> remove(NettyChan.attachChannel(future.channel()));

    private final IntSequence sequence = new IntSequence(DEFAULT_SEQUENCE_STEP);

    private final ConcurrentMap<String, Integer> weights = MapUtils.newConcurrentMap();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notifyCondition = lock.newCondition();
    // attempts to elide conditional wake-ups when the lock is uncontended.
    @SuppressWarnings("all")
    private volatile int signalNeeded = 0; // 0: false, 1: true

    private volatile boolean connecting = false;

    private volatile int capacity = Integer.MAX_VALUE;
    private volatile int warmUp = Constants.DEFAULT_WARM_UP; // warm-up time
    private volatile long timestamp = System.currentTimeMillis();
    private volatile long deadlineMillis = -1;

    public NettyChanGroup(UnresolvedAddress address) {
        this.address = address;
    }

    @Override
    public UnresolvedAddress remoteAddress() {
        return address;
    }

    @Override
    public Chan next() {
        for (;;) {
            // snapshot of channels array
            Object[] elements = channelsUpdater.get(channels);
            int length = elements.length;
            if (length == 0) {
                if (waitForAvailable(1000)) { // wait a moment
                    continue;
                }
                throw new IllegalStateException("No channel");
            }
            if (length == 1) {
                return (Chan) elements[0];
            }

            int index = sequence.next() & Integer.MAX_VALUE;

            return (Chan) elements[index % length];
        }
    }

    @Override
    public List<? extends Chan> channels() {
        return Lists.newArrayList(channels);
    }

    @Override
    public boolean isEmpty() {
        return channels.isEmpty();
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public boolean add(Chan channel) {
        boolean added = channel instanceof NettyChan && channels.add((NettyChan) channel);
        if (added) {
            timestamp = System.currentTimeMillis(); // reset timestamp

            ((NettyChan) channel).channel().closeFuture().addListener(remover);
            deadlineMillis = -1;

            if (signalNeededUpdater.getAndSet(this, 0) != 0) { // signal needed: true
                final ReentrantLock _look = lock;
                _look.lock();
                try {
                    notifyCondition.signalAll(); // must signal all
                } finally {
                    _look.unlock();
                }
            }

            notifyListeners();
        }
        return added;
    }

    @Override
    public boolean remove(Chan channel) {
        boolean removed = channel instanceof NettyChan && channels.remove(channel);
        if (removed) {
            timestamp = System.currentTimeMillis(); // reset timestamp

            if (channels.isEmpty()) {
                deadlineMillis = System.currentTimeMillis() + LOSS_INTERVAL;
            }
        }
        return removed;
    }

    @Override
    public int size() {
        return channels.size();
    }

    @Override
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public boolean isConnecting() {
        return connecting;
    }

    @Override
    public void setConnecting(boolean connecting) {
        this.connecting = connecting;
    }

    @Override
    public boolean isAvailable() {
        return !channels.isEmpty();
    }

    @Override
    public boolean waitForAvailable(long timeoutMillis) {
        boolean available = isAvailable();
        if (available) {
            return true;
        }
        long remains = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        final ReentrantLock _look = lock;
        _look.lock();
        try {
            // avoid "spurious wakeup" occurs
            while (!(available = isAvailable())) {
                signalNeeded = 1; // set signal needed to true
                if ((remains = notifyCondition.awaitNanos(remains)) <= 0) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            ThrowUtil.throwException(e);
        } finally {
            _look.unlock();
        }

        return available;
    }

    @Override
    public void onAvailable(Runnable listener) {
        waitAvailableListeners.add(listener);
        if (isAvailable()) {
            notifyListeners();
        }
    }

    @Override
    public int getWeight(Directory directory) {
        Requires.requireNotNull(directory, "directory");

        Integer weight = weights.get(directory.directoryString());
        return weight == null ? Constants.DEFAULT_WEIGHT : weight;
    }

    @Override
    public void putWeight(Directory directory, int weight) {
        Requires.requireNotNull(directory, "directory");

        if (weight == Constants.DEFAULT_WEIGHT) {
            // the default value does not need to be stored
            return;
        }
        weights.put(directory.directoryString(), weight > Constants.MAX_WEIGHT ? Constants.MAX_WEIGHT : weight);
    }

    @Override
    public void removeWeight(Directory directory) {
        Requires.requireNotNull(directory, "directory");

        weights.remove(directory.directoryString());
    }

    @Override
    public int getWarmUp() {
        return warmUp > 0 ? warmUp : 0;
    }

    @Override
    public void setWarmUp(int warmUp) {
        this.warmUp = warmUp;
    }

    @Override
    public boolean isWarmUpComplete() {
        return System.currentTimeMillis() - timestamp > warmUp;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public long deadlineMillis() {
        return deadlineMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NettyChanGroup that = (NettyChanGroup) o;

        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public String toString() {
        return "NettyChanGroup{" +
                "address=" + address +
                ", channels=" + channels +
                ", weights=" + weights +
                ", warmUp=" + warmUp +
                ", timestamp=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ").format(new Date(timestamp)) +
                ", deadlineMillis=" + deadlineMillis +
                '}';
    }

    void notifyListeners() {
        for (;;) {
            Runnable listener = waitAvailableListeners.poll();
            if (listener == null) {
                break;
            }
            listener.run();
        }
    }
}
