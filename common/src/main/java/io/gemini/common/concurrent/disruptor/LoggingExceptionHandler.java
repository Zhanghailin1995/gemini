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
package io.gemini.common.concurrent.disruptor;

import com.google.common.base.Throwables;
import com.lmax.disruptor.ExceptionHandler;
import io.gemini.common.util.internal.logging.InternalLogger;
import io.gemini.common.util.internal.logging.InternalLoggerFactory;

/**
 * Jupiter
 * org.jupiter.common.concurrent.disruptor
 *
 * @author jiachun.fjc
 */
public class LoggingExceptionHandler implements ExceptionHandler<Object> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(LoggingExceptionHandler.class);

    @Override
    public void handleEventException(Throwable ex, long sequence, Object event) {
        if (logger.isWarnEnabled()) {
            logger.warn("Exception processing: {} {}, {}.", sequence, event, Throwables.getStackTraceAsString(ex));
        }
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        if (logger.isWarnEnabled()) {
            logger.warn("Exception during onStart(), {}.", Throwables.getStackTraceAsString(ex));
        }
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
        if (logger.isWarnEnabled()) {
            logger.warn("Exception during onShutdown(), {}.", Throwables.getStackTraceAsString(ex));
        }
    }
}
