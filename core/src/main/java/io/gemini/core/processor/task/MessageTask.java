package io.gemini.core.processor.task;

import io.gemini.core.processor.DefaultMessageProcessor;
import io.gemini.transport.channel.JChannel;
import io.gemini.transport.payload.JMessagePayload;

/**
 * gemini
 * io.gemini.core.processor.task.MessageTask
 *
 * @author zhanghailin
 */
public class MessageTask implements Runnable {

    // 可能会有很多的参数

    private final DefaultMessageProcessor processor;
    private final JChannel channel;
    private final JMessagePayload message;

    public MessageTask(JChannel channel, DefaultMessageProcessor processor, JMessagePayload message) {
        this.processor = processor;
        this.channel = channel;
        this.message = message;
    }

    @Override
    public void run() {

    }
}
