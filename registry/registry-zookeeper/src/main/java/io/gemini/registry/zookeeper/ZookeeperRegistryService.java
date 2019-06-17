package io.gemini.registry.zookeeper;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import io.gemini.common.util.MapUtils;
import io.gemini.common.util.NetUtil;
import io.gemini.common.util.SpiMetadata;
import io.gemini.common.util.SystemPropertyUtil;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.registry.AbstractRegistryService;
import io.gemini.registry.NotifyListener;
import io.gemini.registry.RegisterMeta;
import io.gemini.registry.RegisterMeta.Address;
import io.netty.util.internal.ConcurrentSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import static io.gemini.common.util.Requires.requireNotNull;

/**
 * gemini
 * io.gemini.registry.zookeeper.ZookeeperRegistryService
 *
 * @author zhanghailin
 */
@SpiMetadata(name = "zookeeper")
public class ZookeeperRegistryService extends AbstractRegistryService {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ZookeeperRegistryService.class);

    // 没有实际意义, 不要在意它
    private static final AtomicLong sequence = new AtomicLong(0);

    private final String address = SystemPropertyUtil.get("gemini.local.address", NetUtil.getLocalAddress());

    private final int sessionTimeoutMs = SystemPropertyUtil.getInt("gemini.registry.zookeeper.sessionTimeoutMs", 60 * 1000);
    private final int connectionTimeoutMs = SystemPropertyUtil.getInt("gemini.registry.zookeeper.connectionTimeoutMs", 15 * 1000);

    private final ConcurrentMap<RegisterMeta.ServiceMeta, PathChildrenCache> pathChildrenCaches = MapUtils.newConcurrentMap();
    // 指定节点都提供了哪些服务
    private final ConcurrentMap<Address, ConcurrentSet<RegisterMeta.ServiceMeta>> serviceMetaMap = MapUtils.newConcurrentMap();

    private CuratorFramework configClient;

    @Override
    public Collection<RegisterMeta> lookup(RegisterMeta.ServiceMeta serviceMeta) {
        String directory = String.format("/gemini/provider/%s/%s/%s",
                serviceMeta.getGroup(),
                serviceMeta.getServiceProviderName(),
                serviceMeta.getVersion());

        List<RegisterMeta> registerMetaList = Lists.newArrayList();
        try {
            List<String> paths = configClient.getChildren().forPath(directory);
            for (String p : paths) {
                registerMetaList.add(parseRegisterMeta(String.format("%s/%s", directory, p)));
            }
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Lookup service meta: {} path failed, {}.", serviceMeta, Throwables.getStackTraceAsString(e));
            }
        }
        return registerMetaList;
    }

    @Override
    protected void doSubscribe(final RegisterMeta.ServiceMeta serviceMeta) {
        PathChildrenCache childrenCache = pathChildrenCaches.get(serviceMeta);
        if (childrenCache == null) {
            String directory = String.format("/jupiter/provider/%s/%s/%s",
                    serviceMeta.getGroup(),
                    serviceMeta.getServiceProviderName(),
                    serviceMeta.getVersion());
            PathChildrenCache newChildrenCache = new PathChildrenCache(configClient, directory, false);
            childrenCache = pathChildrenCaches.putIfAbsent(serviceMeta, newChildrenCache);
            if (childrenCache == null) {
                childrenCache = newChildrenCache;
                childrenCache.getListenable().addListener((client, event) -> {
                    logger.info("Child event: {}", event);

                    switch (event.getType()) {
                        case CHILD_ADDED: {
                            RegisterMeta registerMeta = parseRegisterMeta(event.getData().getPath());
                            Address address = registerMeta.getAddress();
                            RegisterMeta.ServiceMeta serviceMeta1 = registerMeta.getServiceMeta();
                            ConcurrentSet<RegisterMeta.ServiceMeta> serviceMetaSet = getServiceMeta(address);

                            serviceMetaSet.add(serviceMeta1);
                            ZookeeperRegistryService.super.notify(
                                    serviceMeta1,
                                    NotifyListener.NotifyEvent.CHILD_ADDED,
                                    sequence.incrementAndGet(),
                                    registerMeta);

                            break;
                        }
                        case CHILD_REMOVED: {
                            RegisterMeta registerMeta = parseRegisterMeta(event.getData().getPath());
                            Address address = registerMeta.getAddress();
                            RegisterMeta.ServiceMeta serviceMeta1 = registerMeta.getServiceMeta();
                            ConcurrentSet<RegisterMeta.ServiceMeta> serviceMetaSet = getServiceMeta(address);

                            serviceMetaSet.remove(serviceMeta1);
                            ZookeeperRegistryService.super.notify(
                                    serviceMeta1,
                                    NotifyListener.NotifyEvent.CHILD_REMOVED,
                                    sequence.incrementAndGet(),
                                    registerMeta);

                            if (serviceMetaSet.isEmpty()) {
                                logger.info("Offline notify: {}.", address);

                                ZookeeperRegistryService.super.offline(address);
                            }
                            break;
                        }
                    }
                });
            }
        }
    }

    @Override
    public void destroy() {
        configClient.close();
    }

    @Override
    protected void doRegister(final RegisterMeta meta) {
        String directory = String.format("/gemini/provider/%s/%s/%s",
                meta.getGroup(),
                meta.getServiceProviderName(),
                meta.getVersion());

        try {
            // 服务是永久节点
            if (configClient.checkExists().forPath(directory) == null) {
                configClient.create().creatingParentsIfNeeded().forPath(directory);
            }
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Create parent path failed, directory: {}, {}.", directory, Throwables.getStackTraceAsString(e));
            }
        }

        try {
            meta.setHost(address);
            String path = String.format("%s/%s:%s:%s:%s",
                    directory,
                    meta.getHost(),
                    String.valueOf(meta.getPort()),
                    String.valueOf(meta.getWeight()),
                    String.valueOf(meta.getConnCount()));
            logger.info("create EPHEMERAL node,path:[{}]",path);

            // The znode will be deleted upon the client's disconnect.
            configClient.create().withMode(CreateMode.EPHEMERAL).inBackground((client, event) -> {
                if (event.getResultCode() == KeeperException.Code.OK.intValue()) {
                    getRegisterMetaMap().put(meta, RegisterState.DONE);
                }

                logger.info("Register: {} - {}.", meta, event);
            }).forPath(path);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Create register meta: {} path failed, {}.", meta, Throwables.getStackTraceAsString(e));
            }
        }


    }

    @Override
    protected void doUnregister(RegisterMeta meta) {
        String directory = String.format("/gemini/server/%s/%s/%s",
                meta.getGroup(),
                meta.getServiceProviderName(),
                meta.getVersion());

        try {
            if (configClient.checkExists().forPath(directory) == null) {
                return;
            }
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Check exists with parent path failed, directory: {}, {}.", directory, Throwables.getStackTraceAsString(e));
            }
        }

        try {
            meta.setHost(address);

            configClient.delete().inBackground(new BackgroundCallback() {

                @Override
                public void processResult(CuratorFramework client, CuratorEvent event) throws Exception {
                    logger.info("Unregister: {} - {}.", meta, event);
                }
            }).forPath(
                    String.format("%s/%s:%s:%s:%s",
                            directory,
                            meta.getHost(),
                            String.valueOf(meta.getPort()),
                            String.valueOf(meta.getWeight()),
                            String.valueOf(meta.getConnCount())));
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Delete register meta: {} path failed, {}.", meta, Throwables.getStackTraceAsString(e));
            }
        }
    }

    @Override
    protected void doCheckRegisterNodeStatus() {
        for (Map.Entry<RegisterMeta, RegisterState> entry : getRegisterMetaMap().entrySet()) {
            if (entry.getValue() == RegisterState.DONE) {
                continue;
            }

            RegisterMeta meta = entry.getKey();
            String directory = String.format("/gemini/server/%s/%s/%s",
                    meta.getGroup(),
                    meta.getServiceProviderName(),
                    meta.getVersion());

            String nodePath = String.format("%s/%s:%s:%s:%s",
                    directory,
                    meta.getHost(),
                    String.valueOf(meta.getPort()),
                    String.valueOf(meta.getWeight()),
                    String.valueOf(meta.getConnCount()));

            try {
                if (configClient.checkExists().forPath(nodePath) == null) {
                    super.register(meta);
                }
            } catch (Exception e) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Check register status, meta: {} path failed, {}.", meta, Throwables.getStackTraceAsString(e));
                }
            }
        }
    }

    @Override
    public void connectToRegistryServer(String connectString) {

        requireNotNull(connectString, "connectString");

        configClient = CuratorFrameworkFactory.newClient(
                connectString, sessionTimeoutMs, connectionTimeoutMs, new ExponentialBackoffRetry(500, 20));

        configClient.getConnectionStateListenable().addListener((client, newState) -> {

            logger.info("Zookeeper connection state changed {}.", newState);

            if (newState == ConnectionState.RECONNECTED) {

                logger.info("Zookeeper connection has been re-established, will re-subscribe and re-register.");

                // 重新发布服务
                for (RegisterMeta meta : getRegisterMetaMap().keySet()) {
                    ZookeeperRegistryService.super.register(meta);
                }
            }
        });

        configClient.start();
    }

    private RegisterMeta parseRegisterMeta(String data) {
        String[] array_0 = StringUtils.split(data, '/');
        RegisterMeta meta = new RegisterMeta();
        meta.setGroup(array_0[2]);
        meta.setServiceProviderName(array_0[3]);
        meta.setVersion(array_0[4]);

        String[] array_1 = StringUtils.split(array_0[5], ':');
        meta.setHost(array_1[0]);
        meta.setPort(Integer.parseInt(array_1[1]));
        meta.setWeight(Integer.parseInt(array_1[2]));
        meta.setConnCount(Integer.parseInt(array_1[3]));

        return meta;
    }

    private ConcurrentSet<RegisterMeta.ServiceMeta> getServiceMeta(Address address) {
        ConcurrentSet<RegisterMeta.ServiceMeta> serviceMetaSet = serviceMetaMap.get(address);
        if (serviceMetaSet == null) {
            ConcurrentSet<RegisterMeta.ServiceMeta> newServiceMetaSet = new ConcurrentSet<>();
            serviceMetaSet = serviceMetaMap.putIfAbsent(address, newServiceMetaSet);
            if (serviceMetaSet == null) {
                serviceMetaSet = newServiceMetaSet;
            }
        }
        return serviceMetaSet;
    }
}
