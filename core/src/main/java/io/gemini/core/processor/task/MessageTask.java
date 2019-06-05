package io.gemini.core.processor.task;

import io.gemini.common.concurrent.RejectedRunnable;
import io.gemini.core.processor.DefaultMessageProcessor;
import io.gemini.serialization.Serializer;
import io.gemini.serialization.SerializerFactory;
import io.gemini.serialization.SerializerType;
import io.gemini.transport.CodecConfig;
import io.gemini.transport.channel.JChannel;
import io.gemini.transport.payload.JMessagePayload;

/**
 * gemini
 * io.gemini.core.processor.task.MessageTask
 *
 * @author zhanghailin
 */
public class MessageTask implements RejectedRunnable {

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

        // stack copy
        final DefaultMessageProcessor _processor = processor;
        final JMessagePayload _message = message;

        byte serializerCode = _message.serializerCode();
        byte messageCode = _message.messageCode();

        // 是否浅拷贝
        boolean lowCopy = CodecConfig.isCodecLowCopy();
        // protobuf 自己根据生成的类反序列化，不提供工具类
        if (serializerCode == SerializerType.PROTOBUF.value()) {
            if (lowCopy) {

            } else {

            }
        } else {
            Serializer serializer = SerializerFactory.getSerializer(serializerCode);
            if (lowCopy) {

            }
        }


    }

    @Override
    public void rejected() {
        // TODO 任务被拒绝执行
    }
}
