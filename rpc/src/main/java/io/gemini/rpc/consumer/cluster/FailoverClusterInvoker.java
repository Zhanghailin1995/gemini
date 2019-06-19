package io.gemini.rpc.consumer.cluster;

import com.google.common.base.Throwables;
import io.gemini.common.util.Reflects;
import io.gemini.common.util.Requires;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.rpc.Request;
import io.gemini.rpc.consumer.dispatcher.DefaultRoundDispatcher;
import io.gemini.rpc.consumer.dispatcher.Dispatcher;
import io.gemini.rpc.consumer.future.DefaultInvokeFuture;
import io.gemini.rpc.consumer.future.FailoverInvokeFuture;
import io.gemini.rpc.consumer.future.InvokeFuture;
import io.gemini.rpc.model.metadata.MessageWrapper;
import io.gemini.transport.channel.Chan;

/**
 * 失败自动切换, 当出现失败, 重试其它服务器, 要注意的是重试会带来更长的延时.
 *
 * 建议只用于幂等性操作, 通常比较合适用于读操作.
 *
 * 注意failover不能支持广播的调用方式.
 *
 * https://en.wikipedia.org/wiki/Failover
 * gemini
 * io.gemini.rpc.consumer.cluster.FailoverClusterInvoker
 *
 * @author zhanghailin
 */
public class FailoverClusterInvoker implements ClusterInvoker {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(FailoverClusterInvoker.class);

    private final Dispatcher dispatcher;
    private final int retries; // 重试次数, 不包含第一次

    public FailoverClusterInvoker(Dispatcher dispatcher, int retries) {
        Requires.requireTrue(
                dispatcher instanceof DefaultRoundDispatcher,
                Reflects.simpleClassName(dispatcher) + " is unsupported [FailoverClusterInvoker]"
        );

        this.dispatcher = dispatcher;
        if (retries >= 0) {
            this.retries = retries;
        } else {
            this.retries = 2;
        }
    }

    @Override
    public Strategy strategy() {
        return Strategy.FAIL_OVER;
    }

    @Override
    public <T> InvokeFuture<T> invoke(Request request, Class<T> returnType) throws Exception {
        FailoverInvokeFuture<T> future = FailoverInvokeFuture.with(returnType);

        int tryCount = retries + 1;
        invoke0(request, returnType, tryCount, future, null);

        return future;
    }

    private <T> void invoke0(final Request request,
                             final Class<T> returnType,
                             final int tryCount,
                             final FailoverInvokeFuture<T> failOverFuture,
                             final Throwable lastCause) {

        if (tryCount > 0) {
            final InvokeFuture<T> future = dispatcher.dispatch(request, returnType);
            future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    failOverFuture.complete(result);
                } else {
                    if (logger.isWarnEnabled()) {
                        MessageWrapper message = request.message();
                        Chan channel =
                                future instanceof DefaultInvokeFuture ? ((DefaultInvokeFuture) future).channel() : null;

                        logger.warn("[{}]: [Fail-over] retry, [{}] attempts left, [method: {}], [metadata: {}], {}.",
                                channel,
                                tryCount - 1,
                                message.getMethodName(),
                                message.getMetadata(),
                                Throwables.getStackTraceAsString(throwable));
                    }

                    // Note: Failover uses the same invokeId for each call.
                    //
                    // So if the last call triggered the next call because of a timeout,
                    // and then the previous call returned successfully before the next call returns,
                    // will uses the previous call result
                    invoke0(request, returnType, tryCount - 1, failOverFuture, throwable);
                }
            });
        } else {
            failOverFuture.completeExceptionally(lastCause);
        }
    }
}
