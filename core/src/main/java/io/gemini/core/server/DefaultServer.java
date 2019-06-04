package io.gemini.core.server;


import io.gemini.transport.JAcceptor;

public class DefaultServer implements Server {

    // 还需要一个注册到注册中心的服务

    // IO acceptor
    private JAcceptor acceptor;

    @Override
    public JAcceptor acceptor() {
        return acceptor;
    }

    @Override
    public Server withAcceptor(JAcceptor acceptor) {
        this.acceptor = acceptor;
        return this;
    }

    @Override
    public void start() throws InterruptedException {
        acceptor.start();
    }

    @Override
    public void start(boolean sync) throws InterruptedException {
        acceptor.start(sync);
    }

    @Override
    public void shutdownGracefully() {
        acceptor.shutdownGracefully();
    }
}
