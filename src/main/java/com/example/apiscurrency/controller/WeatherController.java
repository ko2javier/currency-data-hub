package com.example.apiscurrency.controller;

import org.springframework.web.bind.annotation.*;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.service.WeatherService;

@RestController
@RequestMapping("/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/{city}")
    public WeatherResponse getWeather(@PathVariable String city) {
        return weatherService.getWeather(city);
    }
}