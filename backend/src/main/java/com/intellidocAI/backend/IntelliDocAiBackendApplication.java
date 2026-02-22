package com.intellidocAI.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableMongoAuditing
public class IntelliDocAiBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelliDocAiBackendApplication.class, args);
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

}
