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

import io.gemini.common.util.Requires;
import io.gemini.common.util.SystemPropertyUtil;
import io.gemini.example.ServiceNonAnnotationTest;
import io.gemini.rpc.Client;
import io.gemini.rpc.DefaultClient;
import io.gemini.rpc.consumer.ProxyFactory;
import io.gemini.rpc.model.metadata.ServiceMetadata;
import io.gemini.serialization.SerializerType;
import io.gemini.transport.Connector;
import io.gemini.transport.exception.ConnectFailedException;
import io.gemini.transport.netty.SimpleNettyTcpConnector;

import java.util.ArrayList;

/**
 * jupiter
 * org.jupiter.example.round
 *
 * @author jiachun.fjc
 */
public class GeminiClient {

    public static void main(String[] args) {
        SystemPropertyUtil.setProperty("jupiter.message.args.allow_null_array_arg", "true");
        SystemPropertyUtil.setProperty("jupiter.serializer.protostuff.allow_null_array_element", "true");
        final Client client = new DefaultClient().withConnector(new SimpleNettyTcpConnector());
        // 连接RegistryServer
        client.connectToRegistryServer("127.0.0.1:20001");
        // 自动管理可用连接
        Connector.ConnectionWatcher watcher = client.watchConnections(
                new ServiceMetadata("test", "io.gemini.example.ServiceNonAnnotationTest", "1.0.0")
        );
        // 等待连接可用
        if (!watcher.waitForAvailable(3000)) {
            throw new ConnectFailedException();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdownGracefully));

        ServiceNonAnnotationTest service = ProxyFactory.factory(ServiceNonAnnotationTest.class)
                .group("test")
                .providerName("io.gemini.example.ServiceNonAnnotationTest")
                .version("1.0.0")
                .client(client)
                .serializerType(SerializerType.PROTO_STUFF)
                .newProxyInstance();

        try {
            String result = service.sayHello(null, null, null);
            Requires.requireTrue("arg1=null, arg2=null, arg3=null".equals(result));
            System.out.println(result);
            result = service.sayHello(null, 1, null);
            Requires.requireTrue("arg1=null, arg2=1, arg3=null".equals(result));
            System.out.println(result);
            result = service.sayHello(null, null, new ArrayList<>());
            Requires.requireTrue("arg1=null, arg2=null, arg3=[]".equals(result));
            System.out.println(result);
            result = service.sayHello("test", 2, null);
            Requires.requireTrue("arg1=test, arg2=2, arg3=null".equals(result));
            System.out.println(result);
            result = service.sayHello("test", null, new ArrayList<>());
            Requires.requireTrue("arg1=test, arg2=null, arg3=[]".equals(result));
            System.out.println(result);
            result = service.sayHello(null, 3, new ArrayList<>());
            Requires.requireTrue("arg1=null, arg2=3, arg3=[]".equals(result));
            System.out.println(result);
            result = service.sayHello("test2", 4, new ArrayList<>());
            Requires.requireTrue("arg1=test2, arg2=4, arg3=[]".equals(result));
            System.out.println(result);
            result = service.sayHello2(new String[] { "a", null, "b" });
            Requires.requireTrue("[a, null, b]".equals(result));
            System.out.println(result);
            result = service.sayHello2(new String[] { null, "a", "b" });
            Requires.requireTrue("[null, a, b]".equals(result));
            System.out.println(result);
            result = service.sayHello2(new String[] { "a", "b", null });
            Requires.requireTrue("[a, b, null]".equals(result));
            System.out.println(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
