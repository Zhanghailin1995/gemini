package io.gemini.rpc;

import io.gemini.common.contants.Constants;
import io.gemini.common.util.Requires;
import io.gemini.common.util.ServiceLoader;
import io.gemini.common.util.Strings;
import io.gemini.common.util.ThrowUtil;
import io.gemini.registry.*;
import io.gemini.rpc.consumer.processor.DefaultConsumerProcessor;
import io.gemini.transport.*;
import io.gemini.transport.channel.ChanGroup;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * gemini
 * io.gemini.rpc.DefaultClient
 *
 * @author zhanghailin
 */
public class DefaultClient implements Client {

    // 服务订阅(SPI)
    private final RegistryService registryService;
    private final String appName;

    private Connector<Connection> connector;

    public DefaultClient() {
        this(Constants.UNKNOWN_APP_NAME, RegistryService.RegistryType.DEFAULT);
    }

    public DefaultClient(RegistryService.RegistryType registryType) {
        this(Constants.UNKNOWN_APP_NAME, registryType);
    }

    public DefaultClient(String appName) {
        this(appName, RegistryService.RegistryType.DEFAULT);
    }

    public DefaultClient(String appName, RegistryService.RegistryType registryType) {
        this.appName = Strings.isBlank(appName) ? Constants.UNKNOWN_APP_NAME : appName;
        registryType = registryType == null ? RegistryService.RegistryType.DEFAULT : registryType;
        registryService = ServiceLoader.load(RegistryService.class).find(registryType.getValue());
    }

    @Override
    public String appName() {
        return appName;
    }

    @Override
    public Connector<Connection> connector() {
        return connector;
    }

    @Override
    public Client withConnector(Connector<Connection> connector) {
        if (connector.processor() == null) {
            connector.withProcessor(new DefaultConsumerProcessor());
        }
        this.connector = connector;
        return this;
    }

    @Override
    public RegistryService registryService() {
        return registryService;
    }

    @Override
    public Collection<RegisterMeta> lookup(Directory directory) {
        RegisterMeta.ServiceMeta serviceMeta = toServiceMeta(directory);
        return registryService.lookup(serviceMeta);
    }

    @Override
    public Connector.ConnectionWatcher watchConnections(Class<?> interfaceClass) {
        return null;
    }

    @Override
    public Connector.ConnectionWatcher watchConnections(Class<?> interfaceClass, String version) {
        return null;
    }

    @Override
    public Connector.ConnectionWatcher watchConnections(Directory directory) {
        Connector.ConnectionWatcher manager = new Connector.ConnectionWatcher() {

            private final ConnectionManager connectionManager = connector.connectionManager();

            private final ReentrantLock lock = new ReentrantLock();
            private final Condition notifyCondition = lock.newCondition();
            // attempts to elide conditional wake-ups when the lock is uncontended.
            private final AtomicBoolean signalNeeded = new AtomicBoolean(false);

            @Override
            public void start() {
                subscribe(directory, new NotifyListener() {

                    /**
                     * 注册中心通知客户端有订阅服务上线
                     * @param registerMeta
                     * @param event
                     */
                    @Override
                    public void notify(RegisterMeta registerMeta, NotifyEvent event) {
                        UnresolvedAddress address = new UnresolvedSocketAddress(registerMeta.getHost(), registerMeta.getPort());
                        final ChanGroup group = connector.group(address);
                        if (event == NotifyEvent.CHILD_ADDED) {
                            if (group.isAvailable()) {
                                onSucceed(group, signalNeeded.getAndSet(false));
                            } else {
                                // 服务正在连接中
                                if (group.isConnecting()) {
                                    group.onAvailable(() -> onSucceed(group, signalNeeded.getAndSet(false)));
                                } else {
                                    // 正在连接中
                                    group.setConnecting(true);
                                    Connection[] connections = connectTo(address, group, registerMeta, true);
                                    final AtomicInteger countdown = new AtomicInteger(connections.length);
                                    for (Connection c : connections) {
                                        c.operationComplete(isSuccess -> {
                                            if (isSuccess) {
                                                onSucceed(group, signalNeeded.getAndSet(false));
                                            }
                                            if (countdown.decrementAndGet() <= 0) {
                                                // 连接已经完成
                                                group.setConnecting(false);
                                            }
                                        });
                                    }
                                }
                            }
                            group.putWeight(directory, registerMeta.getWeight());
                        } else if (event == NotifyEvent.CHILD_REMOVED) {
                            connector.removeChannelGroup(directory, group);
                            group.removeWeight(directory);
                            if (connector.directoryGroup().getRefCount(group) <= 0) {
                                connectionManager.cancelAutoReconnect(address);
                            }
                        }
                    }

                    @SuppressWarnings("SameParameterValue")
                    private Connection[] connectTo(final UnresolvedAddress address, final ChanGroup group, RegisterMeta registerMeta, boolean async) {
                        // 没搞明白为什么同一个客户端和服务端要建立多条连接，为了效率?
                        // 因为是多条连接，所以一个客户端和一个服务端之间的连接就是ChanGroup了，看了好久才明白这个关系，确实没有相同，而且感觉也没有必要吧
                        int connCount = registerMeta.getConnCount(); // global value from single client
                        connCount = connCount < 1 ? 1 : connCount;

                        Connection[] connections = new Connection[connCount];
                        group.setCapacity(connCount);
                        for (int i = 0; i < connCount; i++) {
                            Connection connection = connector.connect(address, async);
                            connections[i] = connection;
                            connectionManager.manage(connection);
                        }

                        offlineListening(address, () -> {
                            connectionManager.cancelAutoReconnect(address);
                            if (!group.isAvailable()) {
                                connector.removeChannelGroup(directory, group);
                            }
                        });

                        return connections;
                    }

                    private void onSucceed(ChanGroup group, boolean doSignal) {
                        connector.addChannelGroup(directory, group);

                        if (doSignal) {
                            final ReentrantLock _look = lock;
                            _look.lock();
                            try {
                                notifyCondition.signalAll();
                            } finally {
                                _look.unlock();
                            }
                        }
                    }
                });
            }

            @Override
            public boolean waitForAvailable(long timeoutMillis) {
                if (connector.isDirectoryAvailable(directory)) {
                    return true;
                }

                long remains = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

                boolean available = false;
                final ReentrantLock _look = lock;
                _look.lock();
                try {
                    signalNeeded.set(true);
                    // avoid "spurious wakeup" occurs
                    while (!(available = connector.isDirectoryAvailable(directory))) {
                        if ((remains = notifyCondition.awaitNanos(remains)) <= 0) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    ThrowUtil.throwException(e);
                } finally {
                    _look.unlock();
                }

                return available || connector.isDirectoryAvailable(directory);
            }
        };

        manager.start();

        return manager;
    }

    @Override
    public boolean awaitConnections(Class<?> interfaceClass, long timeoutMillis) {
        return awaitConnections(interfaceClass, Constants.DEFAULT_VERSION, timeoutMillis);
    }

    @Override
    public boolean awaitConnections(Class<?> interfaceClass, String version, long timeoutMillis) {
        Connector.ConnectionWatcher watcher = watchConnections(interfaceClass, version);
        return watcher.waitForAvailable(timeoutMillis);
    }

    @Override
    public boolean awaitConnections(Directory directory, long timeoutMillis) {
        Connector.ConnectionWatcher watcher = watchConnections(directory);
        return watcher.waitForAvailable(timeoutMillis);
    }

    @Override
    public void subscribe(Directory directory, NotifyListener listener) {
        registryService.subscribe(toServiceMeta(directory), listener);
    }

    @Override
    public void offlineListening(UnresolvedAddress address, OfflineListener listener) {
        if (registryService instanceof AbstractRegistryService) {
            ((AbstractRegistryService) registryService).offlineListening(toAddress(address), listener);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void shutdownGracefully() {
        registryService.shutdownGracefully();
        connector.shutdownGracefully();
    }

    @Override
    public void connectToRegistryServer(String connectString) {
        registryService.connectToRegistryServer(connectString);
    }

    // setter for spring-support
    public void setConnector(Connector<Connection> connector) {
        withConnector(connector);
    }

    private static RegisterMeta.ServiceMeta toServiceMeta(Directory directory) {
        RegisterMeta.ServiceMeta serviceMeta = new RegisterMeta.ServiceMeta();
        serviceMeta.setGroup(Requires.requireNotNull(directory.getGroup(), "group"));
        serviceMeta.setServiceProviderName(Requires.requireNotNull(directory.getServiceProviderName(), "serviceProviderName"));
        serviceMeta.setVersion(Requires.requireNotNull(directory.getVersion(), "version"));
        return serviceMeta;
    }

    private static RegisterMeta.Address toAddress(UnresolvedAddress address) {
        return new RegisterMeta.Address(address.getHost(), address.getPort());
    }
}
