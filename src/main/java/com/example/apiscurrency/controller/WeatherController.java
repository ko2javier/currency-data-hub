package com.example.apiscurrency.controller;

import com.example.apiscurrency.kafka.KafkaEventProducer;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.service.WeatherService;

@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final KafkaEventProducer kafkaEventProducer;

    @Operation(summary = "Get weather by city")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping("/{city}")
    public WeatherResponse getWeather(
            @PathVariable String city,
            Authentication authentication,
            HttpServletRequest request) {

        String username = authentication != null ? authentication.getName() : "anonymous";
        String clientIp = resolveClientIp(request);
        kafkaEventProducer.publishWeatherQuery(username, clientIp, city);

        return weatherService.getWeather(city);
    }

    @Operation(summary = "Endpoint super secreto")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/cache/clear")
    public String clearCache() {
        return "Caché borrada por el administrador.";
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}