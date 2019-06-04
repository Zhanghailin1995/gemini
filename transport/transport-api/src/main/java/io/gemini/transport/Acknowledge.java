package io.gemini.transport;

/**
 * ACK确认消息的包装.
 *
 * jupiter
 * org.jupiter.transport
 *
 * @author jiachun.fjc
 */
public class Acknowledge {

    private final long sequence; // ACK序号

    public Acknowledge(long sequence) {
        this.sequence = sequence;
    }

    public long sequence() {
        return sequence;
    }
}
