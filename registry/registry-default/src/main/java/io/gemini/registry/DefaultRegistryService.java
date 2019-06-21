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
package io.gemini.registry;

import io.gemini.common.util.MapUtils;
import io.gemini.common.util.Requires;
import io.gemini.common.util.SpiMetadata;
import io.gemini.common.util.Strings;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.gemini.transport.Connection;
import io.gemini.transport.UnresolvedAddress;
import io.gemini.transport.UnresolvedSocketAddress;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

/**
 * Default registry service.
 *
 * jupiter
 * org.jupiter.registry.jupiter
 *
 * @author jiachun.fjc
 */
@SpiMetadata(name = "default")
public class DefaultRegistryService extends AbstractRegistryService {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultRegistryService.class);

    // 即使是default 基于内存实现的注册中心也是支持多中心分布式的,所以用一个map来存储,key为注册中心地址,value 为注册中心客户端（每个客户端保持一个对注册中心服务端的连接）
    private final ConcurrentMap<UnresolvedAddress, DefaultRegistry> clients = MapUtils.newConcurrentMap();

    @Override
    protected void doSubscribe(RegisterMeta.ServiceMeta serviceMeta) {
        Collection<DefaultRegistry> allClients = clients.values();
        Requires.requireTrue(!allClients.isEmpty(), "init needed");

        logger.info("Subscribe: {}.", serviceMeta);

        for (DefaultRegistry c : allClients) {
            c.doSubscribe(serviceMeta);
        }
    }

    @Override
    protected void doRegister(RegisterMeta meta) {
        Collection<DefaultRegistry> allClients = clients.values();
        Requires.requireTrue(!allClients.isEmpty(), "init needed");

        logger.info("Register: {}.", meta);

        for (DefaultRegistry c : allClients) {
            c.doRegister(meta);
        }
        getRegisterMetaMap().put(meta, RegisterState.DONE);
    }

    @Override
    protected void doUnregister(RegisterMeta meta) {
        Collection<DefaultRegistry> allClients = clients.values();
        Requires.requireTrue(!allClients.isEmpty(), "init needed");

        logger.info("Unregister: {}.", meta);

        for (DefaultRegistry c : allClients) {
            c.doUnregister(meta);
        }
    }

    @Override
    protected void doCheckRegisterNodeStatus() {
        // the default registry service does nothing
    }

    @Override
    public void connectToRegistryServer(String connectString) {
        Requires.requireNotNull(connectString, "connectString");

        String[] array = Strings.split(connectString, ',');
        for (String s : array) {
            String[] addressStr = Strings.split(s, ':');
            String host = addressStr[0];
            int port = Integer.parseInt(addressStr[1]);
            UnresolvedAddress address = new UnresolvedSocketAddress(host, port);
            DefaultRegistry client = clients.get(address);
            if (client == null) {
                DefaultRegistry newClient = new DefaultRegistry(this);
                client = clients.putIfAbsent(address, newClient);
                if (client == null) {
                    client = newClient;
                    Connection connection = client.connect(address);
                    // 自动管理连接
                    client.connectionManager().manage(connection);
                } else {
                    newClient.shutdownGracefully();
                }
            }
        }
    }

    @Override
    public void destroy() {
        for (DefaultRegistry c : clients.values()) {
            c.shutdownGracefully();
        }
    }
}