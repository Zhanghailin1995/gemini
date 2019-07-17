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
package io.gemini.example.non.annotation;

import io.gemini.common.util.SystemPropertyUtil;
import io.gemini.example.ServiceNonAnnotationTest;
import io.gemini.example.ServiceNonAnnotationTestImpl;
import io.gemini.rpc.DefaultServer;
import io.gemini.rpc.Server;
import io.gemini.rpc.model.metadata.ServiceWrapper;
import io.gemini.transport.netty.SimpleNettyTcpAcceptor;

/**
 * jupiter
 * org.jupiter.example.non.annotation
 *
 * @author jiachun.fjc
 */
public class GeminiServer {

    public static void main(String[] args) {
        SystemPropertyUtil.setProperty("jupiter.message.args.allow_null_array_arg", "true");
        SystemPropertyUtil.setProperty("jupiter.serializer.protostuff.allow_null_array_element", "true");
        final Server server = new DefaultServer().withAcceptor(new SimpleNettyTcpAcceptor(18090));
        //final MonitorServer monitor = new MonitorServer();
        try {
            //monitor.start();

            // provider1
            ServiceNonAnnotationTest service = new ServiceNonAnnotationTestImpl();

            ServiceWrapper provider = server.serviceRegistry()
                    .provider(service)
                    .interfaceClass(ServiceNonAnnotationTest.class)
                    .group("test")
                    .providerName("io.gemini.example.ServiceNonAnnotationTest")
                    .version("1.0.0")
                    .register();

            server.connectToRegistryServer("127.0.0.1:20001");
            server.publish(provider);

            //monitor.shutdownGracefully();
            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownGracefully));

            server.start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
