package io.gemini.rpc.consumer.cluster;

import io.gemini.rpc.Request;
import io.gemini.rpc.consumer.dispatcher.Dispatcher;
import io.gemini.rpc.consumer.future.InvokeFuture;

/**
 * gemini
 * io.gemini.rpc.consumer.cluster.FailfastClusterInvoker
 *
 * @author zhanghailin
 */
public class FailfastClusterInvoker implements ClusterInvoker {

    private final Dispatcher dispatcher;

    public FailfastClusterInvoker(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public Strategy strategy() {
        return Strategy.FAIL_FAST;
    }

    @Override
    public <T> InvokeFuture<T> invoke(Request request, Class<T> returnType) throws Exception {
        return dispatcher.dispatch(request, returnType);
    }
}
