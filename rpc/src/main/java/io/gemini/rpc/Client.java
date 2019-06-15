package io.gemini.rpc;

import io.gemini.registry.*;
import io.gemini.transport.Connection;
import io.gemini.transport.Connector;
import io.gemini.transport.Directory;
import io.gemini.transport.UnresolvedAddress;

import java.util.Collection;

/**
 * gemini
 * io.gemini.rpc.Client
 *
 * @author zhanghailin
 */
public interface Client extends Registry {

    /**
     * 每一个应用都建议设置一个appName.
     */
    String appName();

    /**
     * 网络层connector.
     */
    Connector<Connection> connector();

    /**
     * 设置网络层connector.
     */
    Client withConnector(Connector<Connection> connector);

    /**
     * 注册服务实例
     */
    RegistryService registryService();

    /**
     * 查找服务信息.
     */
    Collection<RegisterMeta> lookup(Directory directory);

    /**
     * 设置对指定服务由jupiter自动管理连接.
     */
    Connector.ConnectionWatcher watchConnections(Class<?> interfaceClass);

    /**
     * 设置对指定服务由jupiter自动管理连接.
     */
    Connector.ConnectionWatcher watchConnections(Class<?> interfaceClass, String version);

    /**
     * 设置对指定服务由jupiter自动管理连接.
     */
    Connector.ConnectionWatcher watchConnections(Directory directory);

    /**
     * 阻塞等待一直到该服务有可用连接或者超时.
     */
    boolean awaitConnections(Class<?> interfaceClass, long timeoutMillis);

    /**
     * 阻塞等待一直到该服务有可用连接或者超时.
     */
    boolean awaitConnections(Class<?> interfaceClass, String version, long timeoutMillis);

    /**
     * 阻塞等待一直到该服务有可用连接或者超时.
     */
    boolean awaitConnections(Directory directory, long timeoutMillis);

    /**
     * 从注册中心订阅一个服务.
     */
    void subscribe(Directory directory, NotifyListener listener);

    /**
     * 服务下线通知.
     */
    void offlineListening(UnresolvedAddress address, OfflineListener listener);

    /**
     * 优雅关闭jupiter client.
     */
    void shutdownGracefully();
}
