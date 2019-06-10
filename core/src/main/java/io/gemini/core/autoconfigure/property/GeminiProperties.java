package io.gemini.core.autoconfigure.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    private int port;

}
