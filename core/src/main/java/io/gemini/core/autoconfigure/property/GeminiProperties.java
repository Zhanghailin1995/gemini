package io.gemini.core.autoconfigure.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    private int port;

    @NestedConfigurationProperty
    private BizThreadPool bizThreadPool = BizThreadPool.DEFAULT;



}
