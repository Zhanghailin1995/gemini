package io.gemini.rpc.provider.processor.task;

import io.gemini.common.concurrent.RejectedRunnable;
import io.gemini.rpc.Request;
import io.gemini.rpc.provider.processor.DefaultProviderProcessor;
import io.gemini.transport.channel.Chan;

/**
 * gemini
 * io.gemini.rpc.provider.processor.task.MessageTask
 *
 * @author zhanghailin
 */
public class MessageTask implements RejectedRunnable {

    private final DefaultProviderProcessor processor;
    private final Chan channel;
    private final Request request;

    public MessageTask(DefaultProviderProcessor processor, Chan channel, Request request) {
        this.processor = processor;
        this.channel = channel;
        this.request = request;
    }


    @Override
    public void run() {

    }

    @Override
    public void rejected() {

    }
}
