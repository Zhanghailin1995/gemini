package io.gemini.core.server;

import io.gemini.transport.JAcceptor;

/**
 * gemini
 * io.gemini.core.server.Server
 *
 * @author zhanghailin
 */
public interface Server {

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
