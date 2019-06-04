package io.gemini.transport.processor;

import io.gemini.transport.channel.JChannel;
import io.gemini.transport.payload.JMessagePayload;

/**
 * gemini
 * io.gemini.transport.processor.MessageProcessor
 *
 * @author zhanghailin
 */
public interface MessageProcessor {

    /**
     * 处理正常请求
     */
    void handleMessage(JChannel channel, JMessagePayload request) throws Exception;

    /**
     * 处理异常
     */
    void handleException(JChannel channel, JMessagePayload message, Throwable cause);

    void shutdown();
}
