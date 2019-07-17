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
package io.gemini.serialization.protostuff;

import io.netty.util.concurrent.FastThreadLocal;
import io.protostuff.LinkedBuffer;

import static io.gemini.serialization.Serializer.DEFAULT_BUF_SIZE;

/**
 * jupiter
 * org.jupiter.serialization.proto.io
 *
 * @author jiachun.fjc
 */
public final class LinkedBuffers {

    // 复用 LinkedBuffer 中链表头结点 byte[]
    private static final FastThreadLocal<LinkedBuffer> bufThreadLocal = new FastThreadLocal<LinkedBuffer>() {

        @Override
        protected LinkedBuffer initialValue() {
            return LinkedBuffer.allocate(DEFAULT_BUF_SIZE);
        }
    };

    public static LinkedBuffer getLinkedBuffer() {
        return bufThreadLocal.get();
    }

    public static void resetBuf(LinkedBuffer buf) {
        buf.clear();
    }

    private LinkedBuffers() {}
}
