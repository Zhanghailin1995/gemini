package io.gemini.transport.payload;

import io.gemini.common.util.LongSequence;

/**
 * gemini
 * io.gemini.transport.payload.JMessagePayload
 *
 * @author zhanghailin
 */
public class JMessagePayload extends PayloadHolder {

    // 请求id自增器, 用于映射 <id, request, response> 三元组
    //
    // id在 <request, response> 生命周期内保证进程内唯一即可, 在Id对应的Response被处理完成后这个id就可以再次使用了,
    // 所以id可在 <Long.MIN_VALUE ~ Long.MAX_VALUE> 范围内从小到大循环利用, 即使溢出也是没关系的, 并且只是从理论上
    // 才有溢出的可能, 比如一个100万qps的系统把 <0 ~ Long.MAX_VALUE> 范围内的id都使用完大概需要29万年.
    //
    // 未来jupiter可能将invokeId限制在48位, 留出高地址的16位作为扩展字段.
    private static final LongSequence sequence = new LongSequence();

    // 用于映射 <id, request, response> 三元组
    private final long invokeId;
    // transport层会在协议解析完成后打上一个时间戳, 用于后续监控对该请求的处理时间
    private transient long timestamp;

    public JMessagePayload() {
        this(sequence.next());
    }

    public JMessagePayload(long invokeId) {
        this.invokeId = invokeId;
    }

    public long invokeId() {
        return invokeId;
    }

    public long timestamp() {
        return timestamp;
    }

    public void timestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
