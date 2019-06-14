package io.gemini.transport;

/**
 * gemini
 * io.gemini.transport.netty.Transporter
 *
 * @author zhanghailin
 */
public interface Transporter {

    /**
     * Returns the transport protocol
     */
    Protocol protocol();

    /**
     * 传输层协议.
     */
    enum Protocol {
        TCP,
        DOMAIN  // Unix domain socket 暂时不做实现 可以实现同主机之间不同进程之间的通信
    }
}
