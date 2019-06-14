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
package io.gemini.transport;

import io.gemini.common.util.MapUtils;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Jupiter的连接管理器, 用于自动管理(按照地址归组)连接.
 *
 * jupiter
 * org.jupiter.transport
 *
 * @author jiachun.fjc
 */
public class ConnectionManager {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ConnectionManager.class);

    private final ConcurrentMap<UnresolvedAddress, CopyOnWriteArrayList<Connection>> connections = MapUtils.newConcurrentMap();

    /**
     * 设置为由jupiter自动管理连接
     */
    public void manage(Connection connection) {
        UnresolvedAddress address = connection.getAddress();
        CopyOnWriteArrayList<Connection> list = connections.get(address);
        if (list == null) {
            CopyOnWriteArrayList<Connection> newList = new CopyOnWriteArrayList<>();
            list = connections.putIfAbsent(address, newList);
            if (list == null) {
                list = newList;
            }
        }
        list.add(connection);
    }

    /**
     * 取消对指定地址的自动重连
     */
    public void cancelAutoReconnect(UnresolvedAddress address) {
        CopyOnWriteArrayList<Connection> list = connections.remove(address);
        if (list != null) {
            for (Connection c : list) {
                c.setReconnect(false);
            }
            logger.warn("Cancel reconnect to: {}.", address);
        }
    }

    /**
     * 取消对所有地址的自动重连
     */
    public void cancelAllAutoReconnect() {
        for (UnresolvedAddress address : connections.keySet()) {
            cancelAutoReconnect(address);
        }
    }
}