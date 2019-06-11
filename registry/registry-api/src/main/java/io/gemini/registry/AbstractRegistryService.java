package io.gemini.registry;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.gemini.common.concurrent.DefaultThreadFactory;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

    private final LinkedBlockingQueue<RegisterMeta> queue = new LinkedBlockingQueue<>();
    private final ExecutorService registerExecutor =
            Executors.newSingleThreadExecutor(new DefaultThreadFactory("register.executor"));
    private final ScheduledExecutorService registerScheduledExecutor =
            Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("register.schedule.executor"));
    private final ExecutorService localRegisterWatchExecutor =
            Executors.newSingleThreadExecutor(new DefaultThreadFactory("local.register.watch.executor"));

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final ConcurrentMap<RegisterMeta.ServiceMeta, RegisterValue> registries =
            Maps.newConcurrentMap();

    // Provider已发布的注册信息
    private final ConcurrentMap<RegisterMeta, RegisterState> registerMetaMap = Maps.newConcurrentMap();

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

    public abstract void destroy();

    protected abstract void doRegister(RegisterMeta meta);

    protected abstract void doUnregister(RegisterMeta meta);

    protected abstract void doCheckRegisterNodeStatus();

    protected ConcurrentMap<RegisterMeta, RegisterState> getRegisterMetaMap() {
        return registerMetaMap;
    }

    protected static class RegisterValue {
        private long version = Long.MIN_VALUE;
        private final Set<RegisterMeta> metaSet = new HashSet<>();
        private final StampedLock lock = new StampedLock(); // segment-lock
    }
}
