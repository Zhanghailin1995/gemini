package io.gemini.core.autoconfigure;

import io.gemini.core.acceptor.SimpleNettyTcpAcceptor;
import io.gemini.core.server.DefaultServer;
import io.gemini.core.server.Server;
import io.gemini.transport.processor.MessageProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

    @Value("gemini.port")
    int port;

    @Autowired
    MessageProcessor processor;

    @Bean
    @ConditionalOnMissingBean
    public Server defaultServer() {
        DefaultServer server = new DefaultServer();
        SimpleNettyTcpAcceptor acceptor = new SimpleNettyTcpAcceptor(port);
        acceptor.setProcessor(processor);
        server.withAcceptor(acceptor);
        return new DefaultServer();
    }
}
