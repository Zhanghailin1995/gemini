package io.gemini.rpc.consumer.invoker;

import io.gemini.rpc.*;
import io.gemini.rpc.consumer.cluster.ClusterInvoker;
import io.gemini.rpc.consumer.dispatcher.Dispatcher;
import io.gemini.rpc.consumer.future.InvokeFuture;
import io.gemini.rpc.model.metadata.ClusterStrategyConfig;
import io.gemini.rpc.model.metadata.MessageWrapper;
import io.gemini.rpc.model.metadata.MethodSpecialConfig;
import io.gemini.rpc.model.metadata.ServiceMetadata;

import java.util.List;

/**
 * gemini
 * io.gemini.rpc.consumer.invoker.AbstractInvoker
 *
 * @author zhanghailin
 */
public abstract class AbstractInvoker implements Invoker {

    private final String appName;
    private final ServiceMetadata metadata; // 目标服务元信息
    private final ClusterStrategyBridging clusterStrategyBridging;

    public AbstractInvoker(String appName,
                           ServiceMetadata metadata,
                           Dispatcher dispatcher,
                           ClusterStrategyConfig defaultStrategy,
                           List<MethodSpecialConfig> methodSpecialConfigs) {
        this.appName = appName;
        this.metadata = metadata;
        clusterStrategyBridging = new ClusterStrategyBridging(dispatcher, defaultStrategy, methodSpecialConfigs);
    }

    protected Object doInvoke(String methodName, Object[] args, Class<?> returnType, boolean sync) throws Throwable {
        Request request = createRequest(methodName, args);
        ClusterInvoker invoker = clusterStrategyBridging.findClusterInvoker(methodName);

        Context invokeCtx = new Context(invoker, returnType, sync);
        Chains.invoke(request, invokeCtx);

        return invokeCtx.getResult();
    }

    private Request createRequest(String methodName, Object[] args) {
        MessageWrapper message = new MessageWrapper(metadata);
        message.setAppName(appName);
        message.setMethodName(methodName);
        // 不需要方法参数类型, 服务端会根据args具体类型按照JLS规则动态dispatch
        message.setArgs(args);

        Request request = new Request();
        request.message(message);

        return request;
    }

    static class Context implements FilterContext {

        private final ClusterInvoker invoker;
        private final Class<?> returnType;
        private final boolean sync;

        private Object result;

        Context(ClusterInvoker invoker, Class<?> returnType, boolean sync) {
            this.invoker = invoker;
            this.returnType = returnType;
            this.sync = sync;
        }

        @Override
        public Filter.Type getType() {
            return Filter.Type.CONSUMER;
        }

        public ClusterInvoker getInvoker() {
            return invoker;
        }

        public Class<?> getReturnType() {
            return returnType;
        }

        public boolean isSync() {
            return sync;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }
    }

    static class ClusterInvokeFilter implements Filter {

        @Override
        public Type getType() {
            return Filter.Type.CONSUMER;
        }

        @Override
        public <T extends FilterContext> void doFilter(Request request, T filterCtx, FilterChain next) throws Throwable {
            Context invokeCtx = (Context) filterCtx;
            ClusterInvoker invoker = invokeCtx.getInvoker();
            Class<?> returnType = invokeCtx.getReturnType();
            // invoke
            InvokeFuture<?> future = invoker.invoke(request, returnType);

            if (invokeCtx.isSync()) {
                invokeCtx.setResult(future.getResult());
            } else {
                invokeCtx.setResult(future);
            }
        }
    }

    static class Chains {

        private static final FilterChain headChain;

        static {
            // 链式调用 Invoker作为最后一个filter 来执行
            FilterChain invokeChain = new DefaultFilterChain(new ClusterInvokeFilter(), null);
            headChain = FilterLoader.loadExtFilters(invokeChain, Filter.Type.CONSUMER);
        }

        static <T extends FilterContext> T invoke(Request request, T invokeCtx) throws Throwable {
            headChain.doFilter(request, invokeCtx);
            return invokeCtx;
        }
    }
}
