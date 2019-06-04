package io.gemini.transport.channel;

import java.util.EventListener;

/**
 * Listen on {@link JChannel}'s event.
 *
 * gemini
 * io.gemini.transport.channel.JChannel
 *
 * @author zhanghailin
 */
public interface JFutureListener<C> extends EventListener {

    void operationSuccess(C c) throws Exception;

    void operationFailure(C c, Throwable cause) throws Exception;
}
