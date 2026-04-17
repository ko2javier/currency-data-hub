package com.example.apiscurrency.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Value("${GATEWAY_URL:http://localhost:7000}")
        private String gatewayUrl;

        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                        .info(new Info()
                                .title("Currency & Weather API")
                                .version("1.0")
                                .description("API Documentación"))
                        .addServersItem(new Server()
                                .url(gatewayUrl)
                                .description("API Gateway"))
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .schemaRequirement("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .description("Mete tu token JWT aquí (sin la palabra Bearer)")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"));
        }
}