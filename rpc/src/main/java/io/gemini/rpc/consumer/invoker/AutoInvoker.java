package io.gemini.rpc.consumer.invoker;

import io.gemini.rpc.consumer.dispatcher.Dispatcher;
import io.gemini.rpc.consumer.future.InvokeFuture;
import io.gemini.rpc.model.metadata.ClusterStrategyConfig;
import io.gemini.rpc.model.metadata.MethodSpecialConfig;
import io.gemini.rpc.model.metadata.ServiceMetadata;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * gemini
 * io.gemini.rpc.consumer.invoker.AutoInvoker
 *
 * @author zhanghailin
 */
public class AutoInvoker extends AbstractInvoker {

    public AutoInvoker(String appName,
                       ServiceMetadata metadata,
                       Dispatcher dispatcher,
                       ClusterStrategyConfig defaultStrategy,
                       List<MethodSpecialConfig> methodSpecialConfigs) {
        super(appName, metadata, dispatcher, defaultStrategy, methodSpecialConfigs);
    }


    @SuppressWarnings("unchecked")
    @RuntimeType
    public Object invoke(@Origin Method method, @AllArguments @RuntimeType Object[] args) throws Throwable {
        Class<?> returnType = method.getReturnType();

        if (isSyncInvoke(returnType)) {
            return doInvoke(method.getName(), args, returnType, true);
        }

        InvokeFuture<Object> inf = (InvokeFuture<Object>) doInvoke(method.getName(), args, returnType, false);

        if (returnType.isAssignableFrom(inf.getClass())) {
            return inf;
        }

        final CompletableFuture<Object> cf = newFuture((Class<CompletableFuture>) returnType);
        inf.whenComplete((result, throwable) -> {
            if (throwable == null) {
                cf.complete(result);
            } else {
                cf.completeExceptionally(throwable);
            }
        });

        return cf;
    }

    private boolean isSyncInvoke(Class<?> returnType) {
        return !CompletableFuture.class.isAssignableFrom(returnType);
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<Object> newFuture(Class<CompletableFuture> cls) {
        try {
            return cls.newInstance();
        } catch (Throwable t) {
            throw new UnsupportedOperationException("fail to create instance with default constructor", t);
        }
    }


}
