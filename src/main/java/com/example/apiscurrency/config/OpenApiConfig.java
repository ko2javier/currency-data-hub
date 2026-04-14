package com.example.apiscurrency.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server; // 🔥 Importante este import
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Currency & Weather API", version = "1.0", description = "API Documentación"),
        security = @SecurityRequirement(name = "bearerAuth"),
        // 🔥 ESTA ES LA MAGIA QUE ENRUTA TODO AL GATEWAY 🔥
        servers = {
                @Server(url = "http://localhost:7000", description = "API Gateway")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        description = "Mete tu token JWT aquí (sin la palabra Bearer)",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}