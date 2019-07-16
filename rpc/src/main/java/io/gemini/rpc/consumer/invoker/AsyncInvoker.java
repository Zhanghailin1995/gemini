package io.gemini.rpc.consumer.invoker;

import io.gemini.common.util.Reflects;
import io.gemini.rpc.consumer.dispatcher.Dispatcher;
import io.gemini.rpc.consumer.future.InvokeFuture;
import io.gemini.rpc.consumer.future.InvokeFutureContext;
import io.gemini.rpc.model.metadata.ClusterStrategyConfig;
import io.gemini.rpc.model.metadata.MethodSpecialConfig;
import io.gemini.rpc.model.metadata.ServiceMetadata;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Asynchronous call, {@link #invoke(Method, Object[])}
 * returns a default value of the corresponding method.
 *
 * 异步调用.
 *
 *
 * gemini
 * io.gemini.rpc.consumer.invoker.AsyncInvoker
 *
 * @author zhanghailin
 */
public class AsyncInvoker extends AbstractInvoker {

    public AsyncInvoker(String appName,
                        ServiceMetadata metadata,
                        Dispatcher dispatcher,
                        ClusterStrategyConfig defaultStrategy,
                        List<MethodSpecialConfig> methodSpecialConfigs) {
        super(appName, metadata, dispatcher, defaultStrategy, methodSpecialConfigs);
    }

    @RuntimeType
    public Object invoke(@Origin Method method, @AllArguments @RuntimeType Object[] args) throws Throwable {
        Class<?> returnType = method.getReturnType();

        Object result = doInvoke(method.getName(), args, returnType, false);

        InvokeFutureContext.set((InvokeFuture<?>) result);

        return Reflects.getTypeDefaultValue(returnType);
    }
}
