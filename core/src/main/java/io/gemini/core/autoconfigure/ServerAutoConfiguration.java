package io.gemini.core.autoconfigure;

import io.gemini.core.acceptor.SimpleNettyTcpAcceptor;
import io.gemini.core.autoconfigure.property.GeminiProperties;
import io.gemini.core.processor.DefaultMessageProcessor;
import io.gemini.core.server.DefaultServer;
import io.gemini.core.server.Server;
import io.gemini.transport.processor.MessageProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gemini
 * io.gemini.core.autoconfigure.ServerAutoConfiguration
 *
 * @author zhanghailin
 */
@Configuration
public class ServerAutoConfiguration {


    @Autowired
    GeminiProperties properties;


    @Bean
    public Server defaultServer() {
        DefaultServer server = new DefaultServer();
        SimpleNettyTcpAcceptor acceptor = new SimpleNettyTcpAcceptor(properties.getPort());
        acceptor.setProcessor(processor());
        server.withAcceptor(acceptor);
        return server;
    }

    @Bean
    public MessageProcessor processor() {
        return new DefaultMessageProcessor();
    }
}
