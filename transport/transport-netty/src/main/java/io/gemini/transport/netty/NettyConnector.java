package io.gemini.transport.netty;

import com.google.common.collect.Maps;
import io.gemini.common.contants.Constants;
import io.gemini.common.util.Requires;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.transport.*;
import io.gemini.transport.channel.ChanGroup;
import io.gemini.transport.channel.CopyOnWriteGroupList;
import io.gemini.transport.channel.DirectoryChanGroup;
import io.gemini.transport.netty.channel.NettyChanGroup;
import io.gemini.transport.netty.estimator.JMessageSizeEstimator;
import io.gemini.transport.processor.ConsumerProcessor;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;

/**
 * gemini
 * io.gemini.transport.netty.NettyConnector
 *
 * @author zhanghailin
 */
public abstract class NettyConnector implements Connector<Connection> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NettyConnector.class);

    //static {
        // touch off DefaultChannelId.<clinit>
        // because getProcessId() sometimes too slow
        //ClassUtil.initializeClass("io.netty.channel.DefaultChannelId", 500);
    //}

    // TCP or DOMAIN now just TCP
    protected final Transporter.Protocol protocol;
    protected final HashedWheelTimer timer = new HashedWheelTimer(new DefaultThreadFactory("connector.timer", true));

    private final ConcurrentMap<UnresolvedAddress, ChanGroup> addressGroups = MapUtils.newConcurrentMap();
    private final DirectoryChanGroup directoryGroup = new DirectoryChanGroup();
    private final ConnectionManager connectionManager = new ConnectionManager();

    private Bootstrap bootstrap;
    private EventLoopGroup worker;
    private int nWorkers;

    private ConsumerProcessor processor;

    public NettyConnector(Transporter.Protocol protocol) {
        this(protocol, Constants.AVAILABLE_PROCESSORS << 1);
    }

    public NettyConnector(Transporter.Protocol protocol, int nWorkers) {
        this.protocol = protocol;
        this.nWorkers = nWorkers;
    }

    protected void init() {
        ThreadFactory workerFactory = workerThreadFactory("jupiter.connector");
        worker = initEventLoopGroup(nWorkers, workerFactory);

        bootstrap = new Bootstrap().group(worker);

        Config child = config();
        child.setOption(Option.IO_RATIO, 100);

        doInit();
    }

    protected abstract void doInit();

    @SuppressWarnings("SameParameterValue")
    protected ThreadFactory workerThreadFactory(String name) {
        return new DefaultThreadFactory(name, Thread.MAX_PRIORITY);
    }

    @Override
    public Transporter.Protocol protocol() {
        return protocol;
    }

    @Override
    public ConsumerProcessor processor() {
        return processor;
    }

    @Override
    public void withProcessor(ConsumerProcessor processor) {
        setProcessor(this.processor = processor);
    }

    @Override
    public ChanGroup group(UnresolvedAddress address) {
        Requires.requireNotNull(address, "address");

        ChanGroup group = addressGroups.get(address);
        if (group == null) {
            ChanGroup newGroup = channelGroup(address);
            group = addressGroups.putIfAbsent(address, newGroup);
            if (group == null) {
                group = newGroup;
            }
        }
        return group;
    }

    @Override
    public Collection<ChanGroup> groups() {
        return addressGroups.values();
    }

    @Override
    public boolean addChannelGroup(Directory directory, ChanGroup group) {
        CopyOnWriteGroupList groups = directory(directory);
        boolean added = groups.addIfAbsent(group);
        if (added) {
            if (logger.isInfoEnabled()) {
                logger.info("Added channel group: {} to {}.", group, directory.directoryString());
            }
        }
        return added;
    }

    @Override
    public boolean removeChannelGroup(Directory directory, ChanGroup group) {
        CopyOnWriteGroupList groups = directory(directory);
        boolean removed = groups.remove(group);
        if (removed) {
            if (logger.isWarnEnabled()) {
                logger.warn("Removed channel group: {} in directory: {}.", group, directory.directoryString());
            }
        }
        return removed;
    }

    @Override
    public CopyOnWriteGroupList directory(Directory directory) {
        return directoryGroup.find(directory);
    }

    @Override
    public boolean isDirectoryAvailable(Directory directory) {
        CopyOnWriteGroupList groups = directory(directory);
        ChanGroup[] snapshot = groups.getSnapshot();
        for (ChanGroup g : snapshot) {
            if (g.isAvailable()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DirectoryChanGroup directoryGroup() {
        return directoryGroup;
    }

    @Override
    public ConnectionManager connectionManager() {
        return connectionManager;
    }

    @Override
    public void shutdownGracefully() {
        connectionManager.cancelAllAutoReconnect();
        worker.shutdownGracefully().syncUninterruptibly();
        timer.stop();
        if (processor != null) {
            processor.shutdown();
        }
    }

    protected void setOptions() {
        Config child = config();

        setIoRatio(child.getOption(Option.IO_RATIO));

        bootstrap.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, JMessageSizeEstimator.DEFAULT);
    }

    /**
     * A {@link Bootstrap} that makes it easy to bootstrap a {@link io.netty.channel.Channel} to use
     * for clients.
     */
    protected Bootstrap bootstrap() {
        return bootstrap;
    }

    /**
     * The {@link EventLoopGroup} for the child. These {@link EventLoopGroup}'s are used to handle
     * all the events and IO for {@link io.netty.channel.Channel}'s.
     */
    protected EventLoopGroup worker() {
        return worker;
    }


    /**
     * Creates the same address of the channel group.
     */
    protected ChanGroup channelGroup(UnresolvedAddress address) {
        return new NettyChanGroup(address);
    }

    /**
     * Sets consumer's processor.
     */
    @SuppressWarnings("unused")
    protected abstract void setProcessor(ConsumerProcessor processor);

    /**
     * Create a WriteBufferWaterMark is used to set low water mark and high water mark for the write buffer.
     */
    protected WriteBufferWaterMark createWriteBufferWaterMark(int bufLowWaterMark, int bufHighWaterMark) {
        WriteBufferWaterMark waterMark;
        if (bufLowWaterMark >= 0 && bufHighWaterMark > 0) {
            waterMark = new WriteBufferWaterMark(bufLowWaterMark, bufHighWaterMark);
        } else {
            waterMark = new WriteBufferWaterMark(512 * 1024, 1024 * 1024);
        }
        return waterMark;
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.
     * The default value is {@code 50}, which means the event loop will try to spend the same
     * amount of time for I/O as for non-I/O tasks.
     */
    public abstract void setIoRatio(int workerIoRatio);



    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory}.
     */
    protected abstract EventLoopGroup initEventLoopGroup(int nThreads, ThreadFactory tFactory);

}
