package io.gemini.transport.channel;

import java.util.EventListener;

/**
 * Listen on {@link Chan}'s event.
 *
 * gemini
 * io.gemini.transport.channel.Chan
 *
 * @author zhanghailin
 */
public interface FutureListener<C> extends EventListener {

    void operationSuccess(C c) throws Exception;

    void operationFailure(C c, Throwable cause) throws Exception;
}
