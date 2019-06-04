package io.gemini.transport;

/**
 * Config group (parent config and child config).
 *
 * 对于网络层的服务端,
 * 通常有一个ServerChannel负责监听并接受连接(它的配置选项对应于 {@link #parent()});
 * 还会有N个负责处理read/write等事件的Channel(它的配置选项对应于 {@link #child()});
 *
 * jupiter
 * org.jupiter.transport
 *
 * @author jiachun.fjc
 */
public interface JConfigGroup {

    /**
     * Config for parent.
     */
    JConfig parent();

    /**
     * Config for child.
     */
    JConfig child();
}
