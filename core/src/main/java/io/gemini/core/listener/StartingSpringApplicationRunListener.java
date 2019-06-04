/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gemini.core.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.context.event.EventPublishingRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Logging starting message {@link SpringApplicationRunListener} before {@link EventPublishingRunListener} execution
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.5.0
 */
public class StartingSpringApplicationRunListener implements SpringApplicationRunListener, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartingSpringApplicationRunListener.class);


    public StartingSpringApplicationRunListener(SpringApplication application, String[] args) {

    }

    @Override
    public void starting() {

    }

    @Override
    public void environmentPrepared(ConfigurableEnvironment environment) {
        System.setProperty("project.version", "All");
    }

    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
    }

    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {

    }

    @Override
    public void started(ConfigurableApplicationContext context) {
        LOGGER.info("Gemini server started successfully...");
    }

    @Override
    public void running(ConfigurableApplicationContext context) {

    }

    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
    }

    /**
     * Before {@link EventPublishingRunListener}
     *
     * @return HIGHEST_PRECEDENCE
     */
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }


}
