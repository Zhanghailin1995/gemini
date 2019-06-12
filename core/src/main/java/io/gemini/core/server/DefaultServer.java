package io.gemini.core.server;


import io.gemini.common.util.ServiceLoader;
import io.gemini.registry.RegisterMeta;
import io.gemini.registry.RegistryService;
import io.gemini.transport.JAcceptor;

public class DefaultServer implements Server {

    // 还需要一个注册到注册中心的服务

    // 服务发布(SPI)
    private final RegistryService registryService;

    public DefaultServer() {
        this(RegistryService.RegistryType.ZOOKEEPER);
    }

    public DefaultServer(RegistryService.RegistryType registryType) {
        registryType = registryType == null ? RegistryService.RegistryType.DEFAULT : registryType;
        // SPI机制
        registryService = ServiceLoader.load(RegistryService.class).find(registryType.getValue());
    }


    // IO acceptor
    private JAcceptor acceptor;

    @Override
    public RegistryService registryService() {
        return registryService;
    }

    @Override
    public ServiceRegistry serviceRegistry() {
        return new DefaultServiceRegistry();
    }

    @Override
    public void connectToRegistryServer(String connectString) {
        registryService.connectToRegistryServer(connectString);
    }

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
        registryService.shutdownGracefully();
        acceptor.shutdownGracefully();
    }

    class DefaultServiceRegistry implements ServiceRegistry {

        private String group;                               // 服务组别
        private String providerName;                        // 服务名称
        private String version;                             // 服务版本号, 通常在接口不兼容时版本号才需要升级
        private int weight;                                 // 权重


        @Override
        public ServiceRegistry group(String group) {
            this.group = group;
            return this;
        }

        @Override
        public ServiceRegistry providerName(String providerName) {
            this.providerName = providerName;
            return this;
        }

        @Override
        public ServiceRegistry version(String version) {
            this.version = version;
            return this;
        }

        @Override
        public ServiceRegistry weight(int weight) {
            this.weight = weight;
            return this;
        }

        @Override
        public RegisterMeta register() {
            RegisterMeta registerMeta = new RegisterMeta();
            registerMeta.setGroup(group);
            registerMeta.setServiceProviderName(providerName);
            registerMeta.setWeight(weight);
            registerMeta.setVersion(version);
            return registerMeta;
        }
    }


}
