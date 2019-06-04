package io.gemini.transport.netty;

import io.gemini.transport.TransportProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Shared heartbeat content.
 * <p>
 * jupiter
 * org.jupiter.transport.netty
 *
 * @author jiachun.fjc
 */
public class Heartbeats {

    private static final ByteBuf HEARTBEAT_BUF;

    static {
        ByteBuf buf = Unpooled.buffer(TransportProtocol.HEADER_SIZE);
        buf.writeShort(TransportProtocol.MAGIC);
        buf.writeByte(TransportProtocol.HEARTBEAT); // 心跳包这里可忽略高地址的4位序列化/反序列化标志
        buf.writeByte(0);
        buf.writeLong(0);
        buf.writeInt(0);
        HEARTBEAT_BUF = Unpooled.unreleasableBuffer(buf).asReadOnly();
    }

    /**
     * Returns the shared heartbeat content.
     */
    public static ByteBuf heartbeatContent() {
        return HEARTBEAT_BUF.duplicate();
    }
}
