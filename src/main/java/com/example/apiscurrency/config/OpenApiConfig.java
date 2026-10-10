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
                                .description("""
                                        **Every endpoint below needs a JWT.** Getting one takes four steps:

                                        1. In the **definition selector at the top right**, switch to **Auth Service**.
                                        2. Run `POST /auth/login` with `{ "username": "user", "password": "user123" }`.
                                        3. Copy the `token` value out of the response.
                                        4. Come back to this definition, click **Authorize**, and paste the token                                         *without* the `Bearer` prefix.

                                        Demo accounts: `user` / `user123` (ROLE_USER) and `admin` / `admin123` (ROLE_ADMIN).

                                        Weather and currency data comes out of a four-level cache: Redis, then MySQL,                                         then the upstream API, falling back to the stale MySQL row if the upstream is down.                                         A repeated query is served from Redis in under a millisecond.
                                        """))
                        .addServersItem(new Server()
                                .url(gatewayUrl)
                                .description("API Gateway"))
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .schemaRequirement("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .description("Paste the JWT from POST /auth/login here, without the \"Bearer \" prefix.")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"));
        }
}