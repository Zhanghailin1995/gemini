package io.gemini.rpc.consumer.dispatcher;

import com.google.common.base.Throwables;
import io.gemini.common.contants.Constants;
import io.gemini.common.util.MapUtils;
import io.gemini.common.util.SystemClock;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.rpc.Client;
import io.gemini.rpc.DispatchType;
import io.gemini.rpc.Request;
import io.gemini.rpc.Response;
import io.gemini.rpc.consumer.ConsumerInterceptor;
import io.gemini.rpc.consumer.future.DefaultInvokeFuture;
import io.gemini.rpc.exception.GeminiRemoteException;
import io.gemini.rpc.loadbalance.LoadBalancer;
import io.gemini.rpc.model.metadata.MessageWrapper;
import io.gemini.rpc.model.metadata.MethodSpecialConfig;
import io.gemini.rpc.model.metadata.ResultWrapper;
import io.gemini.rpc.model.metadata.ServiceMetadata;
import io.gemini.serialization.Serializer;
import io.gemini.serialization.SerializerFactory;
import io.gemini.serialization.SerializerType;
import io.gemini.transport.Status;
import io.gemini.transport.channel.Chan;
import io.gemini.transport.channel.ChanGroup;
import io.gemini.transport.channel.CopyOnWriteGroupList;
import io.gemini.transport.channel.FutureListener;
import io.gemini.transport.payload.RequestPayload;

import java.util.List;
import java.util.Map;

/**
 * gemini
 * io.gemini.rpc.consumer.dispatcher.AbstractDispatcher
 *
 * @author zhanghailin
 */
public abstract class AbstractDispatcher implements Dispatcher {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractDispatcher.class);

    private final Client client;
    private final LoadBalancer loadBalancer;                    // 软负载均衡
    private final Serializer serializerImpl;                    // 序列化/反序列化impl
    private ConsumerInterceptor[] interceptors;                 // 消费者端拦截器
    private long timeoutMillis = Constants.DEFAULT_TIMEOUT;    // 调用超时时间设置
    // 针对指定方法单独设置的超时时间, 方法名为key, 方法参数类型不做区别对待
    private Map<String, Long> methodSpecialTimeoutMapping = MapUtils.newHashMap();

    public AbstractDispatcher(Client client, SerializerType serializerType) {
        this(client, null, serializerType);
    }

    public AbstractDispatcher(Client client, LoadBalancer loadBalancer, SerializerType serializerType) {
        this.client = client;
        this.loadBalancer = loadBalancer;
        this.serializerImpl = SerializerFactory.getSerializer(serializerType.value());
    }

    public Serializer serializer() {
        return serializerImpl;
    }

    public ConsumerInterceptor[] interceptors() {
        return interceptors;
    }

    @Override
    public Dispatcher interceptors(List<ConsumerInterceptor> interceptors) {
        if (interceptors != null && !interceptors.isEmpty()) {
            this.interceptors = interceptors.toArray(new ConsumerInterceptor[0]);
        }
        return this;
    }

    @Override
    public Dispatcher timeoutMillis(long timeoutMillis) {
        if (timeoutMillis > 0) {
            this.timeoutMillis = timeoutMillis;
        }
        return this;
    }

    @Override
    public Dispatcher methodSpecialConfigs(List<MethodSpecialConfig> methodSpecialConfigs) {
        if (!methodSpecialConfigs.isEmpty()) {
            for (MethodSpecialConfig config : methodSpecialConfigs) {
                long timeoutMillis = config.getTimeoutMillis();
                if (timeoutMillis > 0) {
                    methodSpecialTimeoutMapping.put(config.getMethodName(), timeoutMillis);
                }
            }
        }
        return this;
    }

    protected long getMethodSpecialTimeoutMillis(String methodName) {
        Long methodTimeoutMillis = methodSpecialTimeoutMapping.get(methodName);
        if (methodTimeoutMillis != null && methodTimeoutMillis > 0) {
            return methodTimeoutMillis;
        }
        return timeoutMillis;
    }

    protected Chan select(ServiceMetadata metadata) {
        CopyOnWriteGroupList groups = client
                .connector()
                .directory(metadata);
        // ChanGroup 是管理同一个地址的连接，没搞懂一个地址不应该就只有一个连接吗？
        ChanGroup group = loadBalancer.select(groups, metadata);

        if (group != null) {
            if (group.isAvailable()) {
                return group.next();
            }

            // to the deadline (no available channel), the time exceeded the predetermined limit
            long deadline = group.deadlineMillis();
            if (deadline > 0 && SystemClock.millisClock().now() > deadline) {
                boolean removed = groups.remove(group);
                if (removed) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Removed channel group: {} in directory: {} on [select].",
                                group, metadata.directoryString());
                    }
                }
            }
        } else {
            // for 3 seconds, expired not wait
            if (!client.awaitConnections(metadata, 3000)) {
                throw new IllegalStateException("No connections");
            }
        }

        ChanGroup[] snapshot = groups.getSnapshot();
        for (ChanGroup g : snapshot) {
            if (g.isAvailable()) {
                return g.next();
            }
        }

        throw new IllegalStateException("No channel");
    }

    protected ChanGroup[] groups(ServiceMetadata metadata) {
        return client.connector()
                .directory(metadata)
                .getSnapshot();
    }

    protected <T> DefaultInvokeFuture<T> write(
            final Chan channel, final Request request, final Class<T> returnType, final DispatchType dispatchType) {
        final MessageWrapper message = request.message();
        // 指定方法的超时时间
        final long timeoutMillis = getMethodSpecialTimeoutMillis(message.getMethodName());
        final ConsumerInterceptor[] interceptors = interceptors();
        final DefaultInvokeFuture<T> future = DefaultInvokeFuture
                .with(request.invokeId(), channel, timeoutMillis, returnType, dispatchType)
                .interceptors(interceptors);

        // 执行前置拦截器
        if (interceptors != null) {
            for (int i = 0; i < interceptors.length; i++) {
                interceptors[i].beforeInvoke(request, channel);
            }
        }

        final RequestPayload payload = request.payload();

        channel.write(payload, new FutureListener<Chan>() {

            @Override
            public void operationSuccess(Chan channel) throws Exception {
                // 标记已发送
                future.markSent();

                if (dispatchType == DispatchType.ROUND) {
                    payload.clear();
                }
            }

            @Override
            public void operationFailure(Chan channel, Throwable cause) throws Exception {
                if (dispatchType == DispatchType.ROUND) {
                    payload.clear();
                }

                if (logger.isWarnEnabled()) {
                    logger.warn("Writes {} fail on {}, {}.", request, channel, Throwables.getStackTraceAsString(cause));
                }

                ResultWrapper result = new ResultWrapper();
                result.setError(new GeminiRemoteException(cause));

                Response response = new Response(payload.invokeId());
                response.status(Status.CLIENT_ERROR);
                response.result(result);
                // 发送请求失败，doFakeReceived
                DefaultInvokeFuture.fakeReceived(channel, response, dispatchType);
            }
        });

        return future;
    }
}
