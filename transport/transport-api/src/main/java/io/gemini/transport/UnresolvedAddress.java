package io.gemini.transport;

/**
 *
 * gemini
 * io.gemini.transport.UnresolvedAddress
 *
 * @author zhanghailin
 */
public interface UnresolvedAddress {

    String getHost();

    int getPort();

    /**
     * For unix domain socket.
     */
    String getPath();
}
