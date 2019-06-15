package io.gemini.rpc.consumer.invoker;

import java.lang.reflect.Method;

/**
 * gemini
 * io.gemini.rpc.consumer.invoker.Invoker
 *
 * @author zhanghailin
 */
public interface Invoker {

    Object invoke(Method method,Object[] args);
}
