package io.gemini.core.processor;

import com.google.common.base.Throwables;
import io.gemini.core.executor.CloseableExecutor;
import io.gemini.core.processor.task.MessageExecutors;
import io.gemini.core.processor.task.MessageTask;
import io.gemini.transport.channel.JChannel;
import io.gemini.transport.payload.JMessagePayload;
import io.gemini.transport.processor.MessageProcessor;
import lombok.extern.slf4j.Slf4j;

/**
 * gemini
 * io.gemini.core.processor.DefaultMessageProcessor
 * 单例，可以交给Spring控制，注入一些Spring的bean，然后访问数据库等等，处理核心业务
 *
 * @author zhanghailin
 */
@Slf4j
public class DefaultMessageProcessor implements MessageProcessor {


    private final CloseableExecutor executor;


    public DefaultMessageProcessor() {
        this(MessageExecutors.executor());
    }

    public DefaultMessageProcessor(CloseableExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void handleMessage(JChannel channel, JMessagePayload message) throws Exception {
        MessageTask task = new MessageTask(channel, this, message);
        if (executor == null) {
            task.run();
        } else {
            executor.execute(task);
        }
    }

    @Override
    public void handleException(JChannel channel, JMessagePayload message, Throwable cause) {
        log.error("An exception was caught while processing request: {}, {}.",
                channel.remoteAddress(), Throwables.getStackTraceAsString(cause));

        //TODO
    }

    @Override
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
