/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gemini.rpc.model.metadata;

import io.gemini.common.contants.Constants;
import io.gemini.common.util.Pair;
import io.gemini.rpc.Request;
import io.gemini.rpc.flow.control.FlowController;
import io.gemini.rpc.provider.ProviderInterceptor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static io.gemini.common.util.Requires.requireNotNull;


/**
 * Wrapper provider object and service metadata.
 *
 * 服务元数据 & 服务对象
 *
 * jupiter
 * org.jupiter.rpc.model.metadata
 *
 * @author jiachun.fjc
 */
public class ServiceWrapper implements Serializable {

    private static final long serialVersionUID = 6690575889849847348L;

    // 服务元数据
    private final ServiceMetadata metadata;
    // 服务对象
    private final Object serviceProvider;
    // 服务拦截器
    private final ProviderInterceptor[] interceptors;
    // key:     method name
    // value:   pair.first:  方法参数类型(用于根据JLS规则实现方法调用的静态分派)
    //          pair.second: 方法显式声明抛出的异常类型
    private final Map<String, List<Pair<Class<?>[], Class<?>[]>>> extensions;

    // 权重 hashCode() 与 equals() 不把weight计算在内
    private int weight = Constants.DEFAULT_WEIGHT;
    // provider私有线程池
    private Executor executor;
    // provider私有流量控制器
    private FlowController<Request> flowController;

    public ServiceWrapper(String group,
                          String providerName,
                          String version,
                          Object serviceProvider,
                          ProviderInterceptor[] interceptors,
                          Map<String, List<Pair<Class<?>[], Class<?>[]>>> extensions) {

        metadata = new ServiceMetadata(group, providerName, version);

        this.interceptors = interceptors;
        this.extensions = requireNotNull(extensions, "extensions");
        this.serviceProvider = requireNotNull(serviceProvider, "serviceProvider");
    }

    public ServiceMetadata getMetadata() {
        return metadata;
    }

    public Object getServiceProvider() {
        return serviceProvider;
    }

    public ProviderInterceptor[] getInterceptors() {
        return interceptors;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    //TODO add flow controller
    public FlowController<Request> getFlowController() {
        return flowController;
    }

    public void setFlowController(FlowController<Request> flowController) {
        this.flowController = flowController;
    }

    public List<Pair<Class<?>[], Class<?>[]>> getMethodExtension(String methodName) {
        return extensions.get(methodName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceWrapper wrapper = (ServiceWrapper) o;

        return metadata.equals(wrapper.metadata);
    }

    @Override
    public int hashCode() {
        return metadata.hashCode();
    }

    @Override
    public String toString() {
        return "ServiceWrapper{" +
                "metadata=" + metadata +
                ", serviceProvider=" + serviceProvider +
                ", interceptors=" + Arrays.toString(interceptors) +
                ", extensions=" + extensions +
                ", weight=" + weight +
                ", executor=" + executor +
                ", flowController=" + flowController +
                '}';
    }
}
