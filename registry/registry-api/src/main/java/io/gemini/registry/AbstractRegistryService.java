package io.gemini.registry;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.gemini.common.util.MapUtils;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.ConcurrentSet;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;

/**
 * gemini
 * io.gemini.registry.AbstractRegistryService
 *
 * @author zhanghailin
 */
public abstract class AbstractRegistryService implements RegistryService {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractRegistryService.class);

    // 注册队列
    private final LinkedBlockingQueue<RegisterMeta> queue = new LinkedBlockingQueue<>();
    private final ExecutorService registerExecutor =
            Executors.newSingleThreadExecutor(new DefaultThreadFactory("register.executor"));
    private final ScheduledExecutorService registerScheduledExecutor =
            Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("register.schedule.executor"));
    private final ExecutorService localRegisterWatchExecutor =
            Executors.newSingleThreadExecutor(new DefaultThreadFactory("local.register.watch.executor"));

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    // 不知道是干什么的 Jupiter好多地方不加注释，是真的看不懂，不是看不懂，而是不知道为什么这么设计，要看懂需要花很大时间联系上下文，以及其他组件
    // 建议多加注释，有时候只要给方法的参数稍微加一下注释就知道这个方法为什么这么写，以及这个方法是干什么的了
    // 现在懂了KEY : 服务的标识。VALUE: 服务提供者的集合RegisterValue(version?(这个字段是否有必要,因为已经加锁了),Set<RegisterMeta>,StampedLock)
    private final ConcurrentMap<RegisterMeta.ServiceMeta, RegisterValue> registries =
            Maps.newConcurrentMap();

    private final ConcurrentMap<RegisterMeta.ServiceMeta, CopyOnWriteArrayList<NotifyListener>> subscribeListeners =
            Maps.newConcurrentMap();
    private final ConcurrentMap<RegisterMeta.Address, CopyOnWriteArrayList<OfflineListener>> offlineListeners =
            Maps.newConcurrentMap();

    // Consumer已订阅的信息
    // @See ConcurrentSet @deprecated For removal in Netty 4.2. Please use {@link ConcurrentHashMap#newKeySet()} instead
    private final ConcurrentSet<RegisterMeta.ServiceMeta> subscribeSet = new ConcurrentSet<>();
    // Provider已发布的注册信息
    private final ConcurrentMap<RegisterMeta, RegisterState> registerMetaMap = MapUtils.newConcurrentMap();

    public AbstractRegistryService() {
        registerExecutor.execute(() -> {
            while (!shutdown.get()) {
                RegisterMeta meta = null;
                try {
                    meta = queue.take();
                    registerMetaMap.put(meta, RegisterState.PREPARE);
                    doRegister(meta);
                } catch (InterruptedException e) {
                    logger.warn("[register.executor] interrupted.");
                } catch (Throwable t) {
                    if (meta != null) {
                        logger.error("Register [{}] fail: {}, will try again...", meta.toString(), Throwables.getStackTraceAsString(t));

                        // 间隔一段时间再重新入队, 让出cpu
                        final RegisterMeta finalMeta = meta;
                        registerScheduledExecutor.schedule(() -> {
                            queue.add(finalMeta);
                        }, 1, TimeUnit.SECONDS);
                    }
                }
            }
        });

        localRegisterWatchExecutor.execute(() -> {
            while (!shutdown.get()) {
                try {
                    Thread.sleep(3000);
                    doCheckRegisterNodeStatus();
                } catch (InterruptedException e) {
                    logger.warn("[local.register.watch.executor] interrupted.");
                } catch (Throwable t) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Check register node status fail: {}, will try again...", Throwables.getStackTraceAsString(t));
                    }
                }
            }
        });
    }

    @Override
    public void register(RegisterMeta meta) {
        queue.add(meta);
    }

    @Override
    public void unregister(RegisterMeta meta) {
        // 先从queue中移除，如果从queue中移除了说明还没有注册成功
        if (!queue.remove(meta)) {
            // queue中没有说明已经注册成功，从Map集合中unregister
            registerMetaMap.remove(meta);
            doUnregister(meta);
        }
    }

    @Override
    public void subscribe(RegisterMeta.ServiceMeta serviceMeta, NotifyListener listener) {
        CopyOnWriteArrayList<NotifyListener> listeners = subscribeListeners.get(serviceMeta);
        if (listeners == null) {
            CopyOnWriteArrayList<NotifyListener> newListeners = new CopyOnWriteArrayList<>();
            listeners = subscribeListeners.putIfAbsent(serviceMeta, newListeners);
            if (listeners == null) {
                listeners = newListeners;
            }
        }
        listeners.add(listener);

        subscribeSet.add(serviceMeta);
        doSubscribe(serviceMeta);
    }

    @Override
    public Collection<RegisterMeta> lookup(RegisterMeta.ServiceMeta serviceMeta) {
        RegisterValue value = registries.get(serviceMeta);

        if (value == null) {
            return Collections.emptyList();
        }

        // do not try optimistic read
        final StampedLock stampedLock = value.lock;
        final long stamp = stampedLock.readLock();
        try {
            return Lists.newArrayList(value.metaSet);
        } finally {
            stampedLock.unlockRead(stamp);
        }
    }

    @Override
    public Map<RegisterMeta.ServiceMeta, Integer> consumers() {
        Map<RegisterMeta.ServiceMeta, Integer> result = Maps.newHashMap();
        // 有时间研究一下 StampedLock
        for (Map.Entry<RegisterMeta.ServiceMeta, RegisterValue> entry : registries.entrySet()) {
            RegisterValue value = entry.getValue();
            /**
             * long stamp = lock.tryOptimisticRead(); //非阻塞获取版本信息
             * copyVariables2ThreadMemory();//拷贝变量到线程本地堆栈
             * if(!lock.validate(stamp)){ // 校验
             *     long stamp = lock.readLock();//获取读锁
             *     try {
             *         copyVaraibale2ThreadMemory();//拷贝变量到线程本地堆栈
             *      } finally {
             *        lock.unlock(stamp);//释放悲观锁
             *     }
             *
             * }
             *
             * useThreadMemoryVariables();//使用线程本地堆栈里面的数据进行操作
             */
            final StampedLock stampedLock = value.lock;
            long stamp = stampedLock.tryOptimisticRead();
            int optimisticVal = value.metaSet.size();
            if (stampedLock.validate(stamp)) {
                result.put(entry.getKey(), optimisticVal);
                continue;
            }
            stamp = stampedLock.readLock();
            try {
                result.put(entry.getKey(), value.metaSet.size());
            } finally {
                stampedLock.unlockRead(stamp);
            }
        }
        return result;
    }

    @Override
    public Map<RegisterMeta, RegisterState> providers() {
        return new HashMap<>(registerMetaMap);
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    public void shutdownGracefully() {
        if (!shutdown.getAndSet(true)) {
            try {
                registerExecutor.shutdownNow();
                registerScheduledExecutor.shutdownNow();
                localRegisterWatchExecutor.shutdownNow();
            } catch (Exception e) {
                logger.error("Failed to shutdown: {}.", Throwables.getStackTraceAsString(e));
            } finally {
                destroy();
            }
        }
    }

    public void offlineListening(RegisterMeta.Address address, OfflineListener listener) {
        CopyOnWriteArrayList<OfflineListener> listeners = offlineListeners.get(address);
        if (listeners == null) {
            CopyOnWriteArrayList<OfflineListener> newListeners = new CopyOnWriteArrayList<>();
            listeners = offlineListeners.putIfAbsent(address, newListeners);
            if (listeners == null) {
                listeners = newListeners;
            }
        }
        listeners.add(listener);
    }

    public void offline(RegisterMeta.Address address) {
        // remove & notify
        CopyOnWriteArrayList<OfflineListener> listeners = offlineListeners.remove(address);
        if (listeners != null) {
            for (OfflineListener l : listeners) {
                l.offline();
            }
        }
    }

    /**
     * 通知新增或删除服务
     * @param serviceMeta 那个服务
     *
     * @param event 通知的事件
     * @param version
     * @param array
     */
    protected void notify(
            RegisterMeta.ServiceMeta serviceMeta, NotifyListener.NotifyEvent event, long version, RegisterMeta... array) {

        if (array == null || array.length == 0) {
            return;
        }

        RegisterValue value = registries.get(serviceMeta);
        if (value == null) {
            RegisterValue newValue = new RegisterValue();
            value = registries.putIfAbsent(serviceMeta, newValue);
            if (value == null) {
                value = newValue;
            }
        }

        boolean notifyNeeded = false;

        // segment-lock
        final StampedLock stampedLock = value.lock;
        final long stamp = stampedLock.writeLock();
        try {
            // 已经加锁了,是否还有必要使用version字段？
            // 有必要的，并发情况下，如果CHILD_REMOVED时间先触发，那么就没必要在触发CHILD_ADDED，否则会造成服务错乱
            // 防止已经下线的服务假通知上线
            long lastVersion = value.version;
            if (version > lastVersion
                    || (version < 0 && lastVersion > 0 /* version overflow */)) {
                if (event == NotifyListener.NotifyEvent.CHILD_REMOVED) {
                    for (RegisterMeta m : array) {
                        value.metaSet.remove(m);
                    }
                } else if (event == NotifyListener.NotifyEvent.CHILD_ADDED) {
                    Collections.addAll(value.metaSet, array);
                }
                value.version = version;
                notifyNeeded = true;
            }
        } finally {
            stampedLock.unlockWrite(stamp);
        }

        if (notifyNeeded) {
            CopyOnWriteArrayList<NotifyListener> listeners = subscribeListeners.get(serviceMeta);
            if (listeners != null) {
                for (NotifyListener l : listeners) {
                    for (RegisterMeta m : array) {
                        l.notify(m, event);
                    }
                }
            }
        }
    }

    public abstract void destroy();

    protected abstract void doSubscribe(RegisterMeta.ServiceMeta serviceMeta);

    protected abstract void doRegister(RegisterMeta meta);

    protected abstract void doUnregister(RegisterMeta meta);

    protected abstract void doCheckRegisterNodeStatus();

    protected ConcurrentSet<RegisterMeta.ServiceMeta> getSubscribeSet() {
        return subscribeSet;
    }

    protected ConcurrentMap<RegisterMeta, RegisterState> getRegisterMetaMap() {
        return registerMetaMap;
    }

    // 乐观锁?
    protected static class RegisterValue {
        private long version = Long.MIN_VALUE;
        private final Set<RegisterMeta> metaSet = new HashSet<>();
        private final StampedLock lock = new StampedLock(); // segment-lock
    }
}
