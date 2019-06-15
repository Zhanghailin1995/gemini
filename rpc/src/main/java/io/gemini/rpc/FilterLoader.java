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
package io.gemini.rpc;


import com.google.common.base.Throwables;
import io.gemini.common.util.ServiceLoader;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;

import java.util.List;

/**
 * jupiter
 * org.jupiter.rpc
 *
 * @author jiachun.fjc
 */
public class FilterLoader {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(FilterLoader.class);

    public static FilterChain loadExtFilters(FilterChain chain, Filter.Type type) {
        try {
            // 这里使用spi是否不是很好
            List<Filter> sortedList = ServiceLoader.load(Filter.class).sort();

            // 优先级高的在队首
            for (int i = sortedList.size() - 1; i >= 0; i--) {
                Filter extFilter = sortedList.get(i);
                Filter.Type extType = extFilter.getType();
                if (extType == type || extType == Filter.Type.ALL) {
                    // 装饰器模式
                    chain = new DefaultFilterChain(extFilter, chain);
                }
            }
        } catch (Throwable t) {
            logger.error("Failed to load extension filters: {}.", Throwables.getStackTraceAsString(t));
        }

        return chain;
    }
}
