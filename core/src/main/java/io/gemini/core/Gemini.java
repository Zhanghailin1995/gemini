package io.gemini.core;

import io.gemini.core.server.DefaultServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.gemini")
public class Gemini implements CommandLineRunner {

    private DefaultServer server;

    @Autowired
    Gemini(DefaultServer server) {
        this.server = server;
    }

    public static void main(String[] args) {
        SpringApplication.run(Gemini.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdownGracefully();
        }));
        server.start();
    }
}
