package com.example.apiscurrency.service;

import org.springframework.stereotype.Service;
import com.example.apiscurrency.client.WeatherClient;
import com.example.apiscurrency.dto.WeatherResponse;

@Service
public class WeatherService {

    private final WeatherClient weatherClient;

    public WeatherService(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    public WeatherResponse getWeather(String city) {
        return weatherClient.getWeather(city);
    }
}