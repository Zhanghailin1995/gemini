package io.gemini.rpc.consumer.future;

import io.gemini.common.contants.Constants;
import io.gemini.common.util.MapUtils;
import io.gemini.common.util.SystemPropertyUtil;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.rpc.DispatchType;
import io.gemini.rpc.Response;
import io.gemini.rpc.consumer.ConsumerInterceptor;
import io.gemini.rpc.exception.GeminiBizException;
import io.gemini.rpc.exception.GeminiRemoteException;
import io.gemini.rpc.exception.GeminiSerializationException;
import io.gemini.rpc.exception.GeminiTimeoutException;
import io.gemini.rpc.model.metadata.ResultWrapper;
import io.gemini.transport.Status;
import io.gemini.transport.channel.Chan;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * gemini
 * io.gemini.rpc.consumer.future.DefaultInvokeFuture
 *
 * @author zhanghailin
 */
public class DefaultInvokeFuture<V> extends CompletableFuture<V> implements InvokeFuture<V> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultInvokeFuture.class);

    // 默认超时时间,3秒
    private static final long DEFAULT_TIMEOUT_NANOSECONDS = TimeUnit.MILLISECONDS.toNanos(Constants.DEFAULT_TIMEOUT);

    private static final int FUTURES_CONTAINER_INITIAL_CAPACITY =
            SystemPropertyUtil.getInt("io.gemini.rpc.invoke.futures_container_initial_capacity", 1024);
    private static final long TIMEOUT_SCANNER_INTERVAL_MILLIS =
            SystemPropertyUtil.getLong("io.gemini.rpc.invoke.timeout_scanner_interval_millis", 50);

    private static final ConcurrentMap<Long, DefaultInvokeFuture<?>> roundFutures =
            MapUtils.newConcurrentMapLong(FUTURES_CONTAINER_INITIAL_CAPACITY);
    private static final ConcurrentMap<String, DefaultInvokeFuture<?>> broadcastFutures =
            MapUtils.newConcurrentMap(FUTURES_CONTAINER_INITIAL_CAPACITY);

    private static final HashedWheelTimer timeoutScanner =
            new HashedWheelTimer(
                    new DefaultThreadFactory("futures.timeout.scanner", true),
                    TIMEOUT_SCANNER_INTERVAL_MILLIS, TimeUnit.MILLISECONDS,
                    4096
            );

    private final long invokeId; // request.invokeId, 广播的场景可以重复
    private final Chan channel;
    private final Class<V> returnType;
    private final long timeout;
    private final long startTime = System.nanoTime();

    private volatile boolean sent = false;

    private ConsumerInterceptor[] interceptors;

    public static <T> DefaultInvokeFuture<T> with(
            long invokeId, Chan channel, long timeoutMillis, Class<T> returnType, DispatchType dispatchType) {

        return new DefaultInvokeFuture<>(invokeId, channel, timeoutMillis, returnType, dispatchType);
    }

    private DefaultInvokeFuture(
            long invokeId, Chan channel, long timeoutMillis, Class<V> returnType, DispatchType dispatchType) {

        this.invokeId = invokeId;
        this.channel = channel;
        this.timeout = timeoutMillis > 0 ? TimeUnit.MILLISECONDS.toNanos(timeoutMillis) : DEFAULT_TIMEOUT_NANOSECONDS;
        this.returnType = returnType;

        TimeoutTask timeoutTask;

        switch (dispatchType) {
            case ROUND:
                roundFutures.put(invokeId, this);
                timeoutTask = new TimeoutTask(invokeId);
                break;
            case BROADCAST:
                String channelId = channel.id();
                broadcastFutures.put(subInvokeId(channelId, invokeId), this);
                timeoutTask = new TimeoutTask(channelId, invokeId);
                break;
            default:
                throw new IllegalArgumentException("Unsupported " + dispatchType);
        }

        // 使用了netty的HashedWheelTimer，一个时间轮定时器，超过超时时间后会触发task，具体操作看TimeoutTask的run方法
        timeoutScanner.newTimeout(timeoutTask, timeout, TimeUnit.NANOSECONDS);
    }

    public Chan channel() {
        return channel;
    }

    @Override
    public Class<V> returnType() {
        return returnType;
    }

    @Override
    public V getResult() throws Throwable {
        try {
            return get(timeout, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw new GeminiTimeoutException(e, channel.remoteAddress(),
                    sent ? Status.SERVER_TIMEOUT : Status.CLIENT_TIMEOUT);
        }
    }

    public void markSent() {
        sent = true;
    }

    public ConsumerInterceptor[] interceptors() {
        return interceptors;
    }

    public DefaultInvokeFuture<V> interceptors(ConsumerInterceptor[] interceptors) {
        this.interceptors = interceptors;
        return this;
    }

    @SuppressWarnings("all")
    private void doReceived(Response response) {
        byte status = response.status();

        if (status == Status.OK.value()) {
            ResultWrapper wrapper = response.result();
            // 关键步骤，可以查看CompletableFuture的API，会在getResult时候阻塞，直至有返回值，complete方法就是填充返回值，让getResult返回
            complete((V) wrapper.getResult());
        } else {
            setException(status, response);
        }

        // 执行拦截器
        ConsumerInterceptor[] interceptors = this.interceptors; // snapshot
        if (interceptors != null) {
            for (int i = interceptors.length - 1; i >= 0; i--) {
                interceptors[i].afterInvoke(response, channel);
            }
        }
    }

    private void setException(byte status, Response response) {
        Throwable cause;
        if (status == Status.SERVER_TIMEOUT.value()) {
            cause = new GeminiTimeoutException(channel.remoteAddress(), Status.SERVER_TIMEOUT);
        } else if (status == Status.CLIENT_TIMEOUT.value()) {
            cause = new GeminiTimeoutException(channel.remoteAddress(), Status.CLIENT_TIMEOUT);
        } else if (status == Status.DESERIALIZATION_FAIL.value()) {
            ResultWrapper wrapper = response.result();
            cause = (GeminiSerializationException) wrapper.getResult();
        } else if (status == Status.SERVICE_EXPECTED_ERROR.value()) {
            ResultWrapper wrapper = response.result();
            cause = (Throwable) wrapper.getResult();
        } else if (status == Status.SERVICE_UNEXPECTED_ERROR.value()) {
            ResultWrapper wrapper = response.result();
            String message = String.valueOf(wrapper.getResult());
            cause = new GeminiBizException(message, channel.remoteAddress());
        } else {
            ResultWrapper wrapper = response.result();
            Object result = wrapper.getResult();
            if (result instanceof GeminiRemoteException) {
                cause = (GeminiRemoteException) result;
            } else {
                cause = new GeminiRemoteException(response.toString(), channel.remoteAddress());
            }
        }
        completeExceptionally(cause);
    }

    public static void received(Chan channel, Response response) {
        long invokeId = response.id();

        // 从roundFutures中移除 等待返回的future
        DefaultInvokeFuture<?> future = roundFutures.remove(invokeId);

        if (future == null) {
            // 广播场景下做出了一点让步, 多查询了一次roundFutures
            future = broadcastFutures.remove(subInvokeId(channel.id(), invokeId));
        }

        if (future == null) {
            logger.warn("A timeout response [{}] finally returned on {}.", response, channel);
            return;
        }

        future.doReceived(response);
    }

    /**
     * 在发送请求失败的时候会调用此方法
     * @param channel
     * @param response
     * @param dispatchType
     */
    public static void fakeReceived(Chan channel, Response response, DispatchType dispatchType) {
        long invokeId = response.id();

        DefaultInvokeFuture<?> future = null;

        if (dispatchType == DispatchType.ROUND) {
            future = roundFutures.remove(invokeId);
        } else if (dispatchType == DispatchType.BROADCAST) {
            future = broadcastFutures.remove(subInvokeId(channel.id(), invokeId));
        }

        if (future == null) {
            return; // 正确结果在超时被处理之前返回
        }

        future.doReceived(response);
    }

    private static String subInvokeId(String channelId, long invokeId) {
        return channelId + invokeId;
    }

    static final class TimeoutTask implements TimerTask {

        private final String channelId;
        private final long invokeId;

        public TimeoutTask(long invokeId) {
            this.channelId = null;
            this.invokeId = invokeId;
        }

        public TimeoutTask(String channelId, long invokeId) {
            this.channelId = channelId;
            this.invokeId = invokeId;
        }

        @Override
        public void run(Timeout timeout) throws Exception {
            DefaultInvokeFuture<?> future;

            if (channelId == null) {
                // round
                future = roundFutures.remove(invokeId);
            } else {
                // broadcast
                future = broadcastFutures.remove(subInvokeId(channelId, invokeId));
            }

            if (future != null) {
                processTimeout(future);
            }
        }

        private void processTimeout(DefaultInvokeFuture<?> future) {
            // 计算时间是否超时
            if (System.nanoTime() - future.startTime > future.timeout) {
                Response response = new Response(future.invokeId);
                // 超时
                response.status(future.sent ? Status.SERVER_TIMEOUT : Status.CLIENT_TIMEOUT);
                // 返回
                future.doReceived(response);
            }
        }
    }
}
