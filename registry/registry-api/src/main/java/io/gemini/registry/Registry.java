package io.gemini.registry;

/**
 * gemini
 * io.gemini.registry.Registry
 *
 * @author zhanghailin
 */
public interface Registry {

    /**
     * Establish connections with registry server.
     *
     * 连接注册中心, 可连接多个地址.
     *
     * @param connectString list of servers to connect to [host1:port1,host2:port2....]
     */
    void connectToRegistryServer(String connectString);
}
