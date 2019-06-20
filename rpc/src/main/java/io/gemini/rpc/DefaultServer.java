package io.gemini.rpc;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import io.gemini.common.contants.Constants;
import io.gemini.common.util.*;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.registry.RegisterMeta;
import io.gemini.registry.RegistryService;
import io.gemini.rpc.model.metadata.ServiceMetadata;
import io.gemini.rpc.model.metadata.ServiceWrapper;
import io.gemini.rpc.provider.ProviderInterceptor;
import io.gemini.rpc.provider.processor.DefaultProviderProcessor;
import io.gemini.transport.Acceptor;
import io.gemini.transport.Directory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * gemini
 * io.gemini.rpc.DefaultServer
 *
 * @author zhanghailin
 */
public class DefaultServer implements Server {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultServer.class);

    // provider本地容器
    private final ServiceProviderContainer providerContainer = new DefaultServiceProviderContainer();

    // 服务发布(SPI)
    private final RegistryService registryService;

    // 全局拦截器
    private ProviderInterceptor[] globalInterceptors;

    // IO acceptor
    private Acceptor acceptor;

    public DefaultServer() {
        this(RegistryService.RegistryType.ZOOKEEPER);
    }

    public DefaultServer(RegistryService.RegistryType registryType) {
        registryType = registryType == null ? RegistryService.RegistryType.DEFAULT : registryType;
        registryService = ServiceLoader.load(RegistryService.class).find(registryType.getValue());
    }



    @Override
    public Acceptor acceptor() {
        return acceptor;
    }

    @Override
    public Server withAcceptor(Acceptor acceptor) {
        if (acceptor.processor() == null) {
            acceptor.withProcessor(new DefaultProviderProcessor() {
                @Override
                public ServiceWrapper lookupService(Directory directory) {
                    return providerContainer.lookupService(directory.directoryString());
                }
            });
        }
        this.acceptor = acceptor;
        return this;
    }

    @Override
    public RegistryService registryService() {
        return registryService;
    }

    @Override
    public void withGlobalInterceptors(ProviderInterceptor... globalInterceptors) {
        this.globalInterceptors = globalInterceptors;
    }

    @Override
    public ServiceRegistry serviceRegistry() {
        return new DefaultServiceRegistry();
    }

    @Override
    public ServiceWrapper lookupService(Directory directory) {
        return providerContainer.lookupService(directory.directoryString());
    }

    @Override
    public ServiceWrapper removeService(Directory directory) {
        return providerContainer.removeService(directory.directoryString());
    }

    @Override
    public List<ServiceWrapper> allRegisteredServices() {
        return providerContainer.getAllServices();
    }

    @Override
    public void publish(ServiceWrapper serviceWrapper) {
        ServiceMetadata metadata = serviceWrapper.getMetadata();

        RegisterMeta meta = new RegisterMeta();
        meta.setPort(acceptor.boundPort());
        meta.setGroup(metadata.getGroup());
        meta.setServiceProviderName(metadata.getServiceProviderName());
        meta.setVersion(metadata.getVersion());
        meta.setWeight(serviceWrapper.getWeight());
        meta.setConnCount(Constants.SUGGESTED_CONNECTION_COUNT);

        // zookeeper 注册中心是如何注册的，简单解释一下，因为代码量太大，怕搞忘记了
        // register并没有直接注册，而是将RegisterMeta 放入队列中，然后启动了一个单线程的线程池，
        // 这个单线程的线程池不停的从队列中取数据，然后写入zookeeper
        // 大量使用了SPI机制,只需要引入相关的jar就可以使用相关的注册中心
        registryService.register(meta);
    }

    @Override
    public void publish(ServiceWrapper... serviceWrappers) {
        for (ServiceWrapper wrapper : serviceWrappers) {
            publish(wrapper);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> void publishWithInitializer(
            final ServiceWrapper serviceWrapper, final ProviderInitializer<T> initializer, Executor executor) {
        Runnable task = () -> {
            try {
                initializer.init((T) serviceWrapper.getServiceProvider());
                publish(serviceWrapper);
            } catch (Exception e) {
                logger.error("Error on {} #publishWithInitializer: {}.", serviceWrapper.getMetadata(),
                        Throwables.getStackTraceAsString(e));
            }
        };

        if (executor == null) {
            task.run();
        } else {
            executor.execute(task);
        }

    }

    @Override
    public void publishAll() {
        for (ServiceWrapper wrapper : providerContainer.getAllServices()) {
            publish(wrapper);
        }
    }

    @Override
    public void unpublish(ServiceWrapper serviceWrapper) {
        ServiceMetadata metadata = serviceWrapper.getMetadata();

        RegisterMeta meta = new RegisterMeta();
        meta.setPort(acceptor.boundPort());
        meta.setGroup(metadata.getGroup());
        meta.setVersion(metadata.getVersion());
        meta.setServiceProviderName(metadata.getServiceProviderName());
        meta.setWeight(serviceWrapper.getWeight());
        meta.setConnCount(Constants.SUGGESTED_CONNECTION_COUNT);

        registryService.unregister(meta);
    }

    @Override
    public void unpublishAll() {
        for (ServiceWrapper wrapper : providerContainer.getAllServices()) {
            unpublish(wrapper);
        }
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

    @Override
    public void connectToRegistryServer(String connectString) {
        registryService.connectToRegistryServer(connectString);
    }

    public void setAcceptor(Acceptor acceptor) {
        withAcceptor(acceptor);
    }

    ServiceWrapper registerService(
            String group,
            String providerName,
            String version,
            Object serviceProvider,
            ProviderInterceptor[] interceptors,
            Map<String, List<Pair<Class<?>[], Class<?>[]>>> extensions,
            int weight,
            Executor executor
            //,FlowController<Request> flowController
    ) {

        ProviderInterceptor[] allInterceptors = null;
        List<ProviderInterceptor> tempList = Lists.newArrayList();
        if (globalInterceptors != null) {
            Collections.addAll(tempList, globalInterceptors);
        }
        if (interceptors != null) {
            Collections.addAll(tempList, interceptors);
        }
        if (!tempList.isEmpty()) {
            allInterceptors = tempList.toArray(new ProviderInterceptor[0]);
        }

        ServiceWrapper wrapper =
                new ServiceWrapper(group, providerName, version, serviceProvider, allInterceptors, extensions);

        wrapper.setWeight(weight);
        wrapper.setExecutor(executor);
        //wrapper.setFlowController(flowController);

        //group providerName,version 构成了一个确定服务的三元组

        providerContainer.registerService(wrapper.getMetadata().directoryString(), wrapper);

        return wrapper;
    }

    class DefaultServiceRegistry implements ServiceRegistry {

        private Object serviceProvider;                     // 服务对象
        private ProviderInterceptor[] interceptors;         // 拦截器
        private Class<?> interfaceClass;                    // 接口类型
        private String group;                               // 服务组别
        private String providerName;                        // 服务名称
        private String version;                             // 服务版本号, 通常在接口不兼容时版本号才需要升级
        private int weight;                                 // 权重
        private Executor executor;                          // 该服务私有的线程池
        //private FlowController<JRequest> flowController;    // 该服务私有的流量控制器

        @Override
        public ServiceRegistry provider(Object serviceProvider, ProviderInterceptor... interceptors) {
            this.serviceProvider = serviceProvider;
            this.interceptors = interceptors;
            return this;
        }

        @Override
        public ServiceRegistry interfaceClass(Class<?> interfaceClass) {
            this.interfaceClass = interfaceClass;
            return this;
        }

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
        public ServiceRegistry executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /*@Override
        public ServiceRegistry flowController(FlowController<JRequest> flowController) {
            this.flowController = flowController;
            return this;
        }*/

        @Override
        public ServiceWrapper register() {
            Requires.requireNotNull(serviceProvider, "serviceProvider");

            Class<?> providerClass = serviceProvider.getClass();

            ServiceProviderImpl implAnnotation = null;
            ServiceProvider ifAnnotation = null;
            // 这个循环的作用，找到服务类的实现类和接口
            for (Class<?> cls = providerClass; cls != Object.class; cls = cls.getSuperclass()) {
                if (implAnnotation == null) {
                    implAnnotation = cls.getAnnotation(ServiceProviderImpl.class);
                }

                Class<?>[] interfaces = cls.getInterfaces();
                if (interfaces != null) {
                    for (Class<?> i : interfaces) {
                        ifAnnotation = i.getAnnotation(ServiceProvider.class);
                        if (ifAnnotation == null) {
                            continue;
                        }

                        Requires.requireTrue(
                                interfaceClass == null,
                                i.getName() + " has a @ServiceProvider annotation, can't set [interfaceClass] again"
                        );

                        interfaceClass = i;
                        break;
                    }
                }

                if (implAnnotation != null && ifAnnotation != null) {
                    break;
                }
            }

            if (ifAnnotation != null) {
                Requires.requireTrue(
                        group == null,
                        interfaceClass.getName() + " has a @ServiceProvider annotation, can't set [group] again"
                );
                Requires.requireTrue(
                        providerName == null,
                        interfaceClass.getName() + " has a @ServiceProvider annotation, can't set [providerName] again"
                );

                group = ifAnnotation.group();
                String name = ifAnnotation.name();
                // providerName 根据用户传入的那么，或者取interfaceClass的name
                providerName = Strings.isNotBlank(name) ? name : interfaceClass.getName();
            }

            if (implAnnotation != null) {
                Requires.requireTrue(
                        version == null,
                        providerClass.getName() + " has a @ServiceProviderImpl annotation, can't set [version] again"
                );

                version = implAnnotation.version();
            }

            Requires.requireNotNull(interfaceClass, "interfaceClass");
            Requires.requireTrue(Strings.isNotBlank(group), "group");
            Requires.requireTrue(Strings.isNotBlank(providerName), "providerName");
            Requires.requireTrue(Strings.isNotBlank(version), "version");

            // method's extensions
            //
            // key:     method name
            // value:   pair.first:  方法参数类型(用于根据JLS规则实现方法调用的静态分派)
            //          pair.second: 方法显式声明抛出的异常类型
            // 似乎不支持方法重载? 支持的，是我搞错了.
            // 如果有重载方法,value list 的size就会大于1 {key="io.gemini.service.UserService",value=[<paramTypeCls1,ExceptionCls1>,<paramTypeCls2,ExceptionCls2>]}
            Map<String, List<Pair<Class<?>[], Class<?>[]>>> extensions = MapUtils.newHashMap();
            for (Method method : interfaceClass.getMethods()) {
                String methodName = method.getName();
                List<Pair<Class<?>[], Class<?>[]>> list = extensions.computeIfAbsent(methodName, k -> Lists.newArrayList());
                list.add(Pair.of(method.getParameterTypes(), method.getExceptionTypes()));
            }

            return registerService(
                    group,
                    providerName,
                    version,
                    serviceProvider,
                    interceptors,
                    extensions,
                    weight,
                    executor
                    //,
                    //flowController
            );
        }
    }


    /**
     * Local service provider container.
     *
     * 本地provider容器
     */
    interface ServiceProviderContainer {

        /**
         * 注册服务(注意并不是发布服务到注册中心, 只是注册到本地容器)
         */
        void registerService(String uniqueKey, ServiceWrapper serviceWrapper);

        /**
         * 本地容器查找服务
         */
        ServiceWrapper lookupService(String uniqueKey);

        /**
         * 从本地容器移除服务
         */
        ServiceWrapper removeService(String uniqueKey);

        /**
         * 获取本地容器中所有服务
         */
        List<ServiceWrapper> getAllServices();
    }

    // 本地provider容器默认实现
    private static final class DefaultServiceProviderContainer implements ServiceProviderContainer {

        private final ConcurrentMap<String, ServiceWrapper> serviceProviders = MapUtils.newConcurrentMap();

        @Override
        public void registerService(String uniqueKey, ServiceWrapper serviceWrapper) {
            serviceProviders.put(uniqueKey, serviceWrapper);

            logger.info("ServiceProvider [{}, {}] is registered.", uniqueKey, serviceWrapper);
        }

        @Override
        public ServiceWrapper lookupService(String uniqueKey) {
            return serviceProviders.get(uniqueKey);
        }

        @Override
        public ServiceWrapper removeService(String uniqueKey) {
            ServiceWrapper serviceWrapper = serviceProviders.remove(uniqueKey);
            if (serviceWrapper == null) {
                logger.warn("ServiceProvider [{}] not found.", uniqueKey);
            } else {
                logger.info("ServiceProvider [{}, {}] is removed.", uniqueKey, serviceWrapper);
            }
            return serviceWrapper;
        }

        @Override
        public List<ServiceWrapper> getAllServices() {
            return Lists.newArrayList(serviceProviders.values());
        }
    }
}
