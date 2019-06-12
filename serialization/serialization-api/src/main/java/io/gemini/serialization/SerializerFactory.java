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
package io.gemini.serialization;

import io.gemini.common.util.ServiceLoader;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;
import io.netty.util.collection.ByteObjectHashMap;
import io.netty.util.collection.ByteObjectMap;

/**
 * Holds all serializers.
 *
 * jupiter
 * org.jupiter.serialization
 *
 * @author jiachun.fjc
 */
public final class SerializerFactory {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SerializerFactory.class);

    private static final ByteObjectMap<Serializer> serializers = new ByteObjectHashMap<>();

    static {
        Iterable<Serializer> all = ServiceLoader.load(Serializer.class);
        for (Serializer s : all) {
            serializers.put(s.code(), s);
        }
        logger.info("Supported serializers: {}.", serializers);
    }

    public static Serializer getSerializer(byte code) {
        Serializer serializer = serializers.get(code);

        if (serializer == null) {
            SerializerType type = SerializerType.parse(code);
            if (type != null) {
                throw new IllegalArgumentException("Serializer implementation [" + type.name() + "] not found");
            } else {
                throw new IllegalArgumentException("Unsupported serializer type with code: " + code);
            }
        }

        return serializer;
    }

    private SerializerFactory() {}
}
