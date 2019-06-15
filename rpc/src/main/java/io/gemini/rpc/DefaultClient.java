package io.gemini.rpc;

import io.gemini.common.contants.Constants;
import io.gemini.common.util.ServiceLoader;
import io.gemini.common.util.Strings;
import io.gemini.registry.NotifyListener;
import io.gemini.registry.OfflineListener;
import io.gemini.registry.RegisterMeta;
import io.gemini.registry.RegistryService;
import io.gemini.rpc.consumer.processor.DefaultConsumerProcessor;
import io.gemini.transport.Connection;
import io.gemini.transport.Connector;
import io.gemini.transport.Directory;
import io.gemini.transport.UnresolvedAddress;

import java.util.Collection;

/**
 * gemini
 * io.gemini.rpc.DefaultClient
 *
 * @author zhanghailin
 */
public class DefaultClient implements Client {

    // 服务订阅(SPI)
    private final RegistryService registryService;
    private final String appName;

    private Connector<Connection> connector;

    public DefaultClient() {
        this(Constants.UNKNOWN_APP_NAME, RegistryService.RegistryType.DEFAULT);
    }

    public DefaultClient(RegistryService.RegistryType registryType) {
        this(Constants.UNKNOWN_APP_NAME, registryType);
    }

    public DefaultClient(String appName) {
        this(appName, RegistryService.RegistryType.DEFAULT);
    }

    public DefaultClient(String appName, RegistryService.RegistryType registryType) {
        this.appName = Strings.isBlank(appName) ? Constants.UNKNOWN_APP_NAME : appName;
        registryType = registryType == null ? RegistryService.RegistryType.DEFAULT : registryType;
        registryService = ServiceLoader.load(RegistryService.class).find(registryType.getValue());
    }

    @Override
    public String appName() {
        return appName;
    }

    @Override
    public Connector<Connection> connector() {
        return connector;
    }

    @Override
    public Client withConnector(Connector<Connection> connector) {
        if (connector.processor() == null) {
            connector.withProcessor(new DefaultConsumerProcessor());
        }
        this.connector = connector;
        return this;
    }

    @Override
    public RegistryService registryService() {
        return null;
    }

    @Override
    public Collection<RegisterMeta> lookup(Directory directory) {
        return null;
    }

    @Override
    public Connector.ConnectionWatcher watchConnections(Class<?> interfaceClass) {
        return null;
    }

    @Override
    public Connector.ConnectionWatcher watchConnections(Class<?> interfaceClass, String version) {
        return null;
    }

    @Override
    public Connector.ConnectionWatcher watchConnections(Directory directory) {
        return null;
    }

    @Override
    public boolean awaitConnections(Class<?> interfaceClass, long timeoutMillis) {
        return false;
    }

    @Override
    public boolean awaitConnections(Class<?> interfaceClass, String version, long timeoutMillis) {
        return false;
    }

    @Override
    public boolean awaitConnections(Directory directory, long timeoutMillis) {
        return false;
    }

    @Override
    public void subscribe(Directory directory, NotifyListener listener) {

    }

    @Override
    public void offlineListening(UnresolvedAddress address, OfflineListener listener) {

    }

    @Override
    public void shutdownGracefully() {

    }

    @Override
    public void connectToRegistryServer(String connectString) {

    }
}
