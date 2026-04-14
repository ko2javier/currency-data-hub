package com.example.apiscurrency.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.service.WeatherService;

@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor // Usando la magia de Lombok que vimos antes ;)
public class WeatherController {

    private final WeatherService weatherService;

    @Operation(summary = "Get weather by city")
    // 🔥 Solo los usuarios con ROLE_USER (o ADMIN) pueden pedir el clima normal
    //@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{city}")
    public WeatherResponse getWeather(@PathVariable String city) {
        return weatherService.getWeather(city);
    }

    @Operation(summary = "Endpoint super secreto")
    // 🔥 SOLO los administradores pueden entrar aquí
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/cache/clear")
    public String clearCache() {
        return "Caché borrada por el administrador.";
    }
}