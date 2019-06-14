package io.gemini.core.processor.task;

import io.gemini.common.concurrent.RejectedRunnable;
import io.gemini.core.model.Message;
import io.gemini.core.processor.DefaultMessageProcessor;
import io.gemini.serialization.Serializer;
import io.gemini.serialization.SerializerFactory;
import io.gemini.serialization.SerializerType;
import io.gemini.transport.CodecConfig;
import io.gemini.transport.channel.Chan;

/**
 * gemini
 * io.gemini.core.processor.task.MessageTask
 *
 * @author zhanghailin
 */
public class MessageTask implements RejectedRunnable {

    // 可能会有很多的参数

    private final DefaultMessageProcessor processor;
    private final Chan channel;
    private final JMessagePayload message;

    public MessageTask(Chan channel, DefaultMessageProcessor processor, JMessagePayload message) {
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
        Message msg;
        // protobuf 自己根据生成的类反序列化，不提供工具类
        if (serializerCode == SerializerType.PROTOBUF.value()) {
            if (lowCopy) {
                //protobuf暂时不支持low_copy
                //原因 我不知道protobuf的low_copy怎么做
            } else {

            }
        } else {
            Serializer serializer = SerializerFactory.getSerializer(serializerCode);
            if (lowCopy) {

            } else {
                byte[] bytes = _message.bytes();
                msg = serializer.readObject(bytes, Message.class);
            }
        }


    }

    @Override
    public void rejected() {
        // TODO 任务被拒绝执行
    }
}
