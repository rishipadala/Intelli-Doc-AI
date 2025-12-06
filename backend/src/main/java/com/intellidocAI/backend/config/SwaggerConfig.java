package com.intellidocAI.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                // 1. General Info
                .info(new Info()
                        .title("IntelliDoc AI API")
                        .version("1.0")
                        .description("API documentation for the IntelliDoc AI Code Documentation Generator.")
                        .contact(new Contact().name("Rishi").email("rvpadala20@gmail.com"))
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))

                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")
                ))

                // 2. Setup Security (JWT Bearer Token)
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token here to access protected endpoints.")));
    }
}
