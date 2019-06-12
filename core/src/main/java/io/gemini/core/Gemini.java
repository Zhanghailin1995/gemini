package io.gemini.core;

import io.gemini.core.server.Server;
import io.gemini.registry.RegisterMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.gemini")
public class Gemini implements CommandLineRunner {

    @Autowired
    private Server server;

    public static void main(String[] args) {
        SpringApplication.run(Gemini.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdownGracefully();
        }));
        server.connectToRegistryServer("127.0.0.1:2181");
        RegisterMeta registerMeta = server.serviceRegistry().group("default").providerName("imserver").version("0.0.1").register();
        registerMeta.setPort(server.acceptor().boundPort());
        server.registryService().register(registerMeta);
        server.start();
    }
}
