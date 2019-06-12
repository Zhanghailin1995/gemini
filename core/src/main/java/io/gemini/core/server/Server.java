package io.gemini.core.server;

import io.gemini.registry.RegisterMeta;
import io.gemini.registry.Registry;
import io.gemini.registry.RegistryService;
import io.gemini.transport.JAcceptor;


/**
 * gemini
 * io.gemini.core.server.Server
 *
 * @author zhanghailin
 */
public interface Server extends Registry {

    /**
     * 本地服务注册.
     */
    interface ServiceRegistry {
        /**
         * 设置服务组别
         */
        ServiceRegistry group(String group);

        /**
         * 设置服务版本号
         */
        ServiceRegistry version(String version);

        /**
         * 设置服务权重(0 < weight <= 100).
         */
        ServiceRegistry weight(int weight);

        /**
         * 设置服务名称
         */
        ServiceRegistry providerName(String providerName);

        RegisterMeta register();
    }

    /**
     * 注册服务实例
     */
    RegistryService registryService();

    /**
     * 获取服务注册(本地)工具.
     */
    ServiceRegistry serviceRegistry();

    /**
     * 网络层acceptor.
     */
    JAcceptor acceptor();

    /**
     * 设置网络层acceptor.
     */
    Server withAcceptor(JAcceptor acceptor);

    /**
     * 启动gemini core im server, 以同步阻塞的方式启动.
     */
    void start() throws InterruptedException;

    /**
     * 启动gemini core im server, 可通过参数指定异步/同步的方式启动.
     */
    void start(boolean sync) throws InterruptedException;

    /**
     * 优雅关闭jupiter server.
     */
    void shutdownGracefully();
}
