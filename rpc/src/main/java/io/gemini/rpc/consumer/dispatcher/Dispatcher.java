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
package io.gemini.rpc.consumer.dispatcher;


import io.gemini.rpc.Request;
import io.gemini.rpc.consumer.ConsumerInterceptor;
import io.gemini.rpc.consumer.future.InvokeFuture;
import io.gemini.rpc.model.metadata.MethodSpecialConfig;

import java.util.List;

/**
 * jupiter
 * org.jupiter.rpc.consumer.dispatcher
 *
 * @author jiachun.fjc
 */
public interface Dispatcher {

    <T> InvokeFuture<T> dispatch(Request request, Class<T> returnType);

    Dispatcher interceptors(List<ConsumerInterceptor> interceptors);

    Dispatcher timeoutMillis(long timeoutMillis);

    Dispatcher methodSpecialConfigs(List<MethodSpecialConfig> methodSpecialConfigs);
}
