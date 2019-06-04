package io.gemini.core.autoconfigure;

import io.gemini.core.autoconfigure.property.GeminiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@org.springframework.context.annotation.Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiAutoConfiguration {

    private final GeminiProperties properties;

    public GeminiAutoConfiguration(GeminiProperties properties) {
        this.properties = properties;
    }


}
