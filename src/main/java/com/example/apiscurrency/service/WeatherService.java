package com.example.apiscurrency.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import com.example.apiscurrency.client.WeatherClient;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.dto.CurrentWeather;
import com.example.apiscurrency.model.WeatherCache;
import com.example.apiscurrency.repository.WeatherCacheRepository;

@Service
public class WeatherService {

    private final WeatherClient weatherClient;
    private final WeatherCacheRepository repository;

    public WeatherService(WeatherClient weatherClient, WeatherCacheRepository repository) {
        this.weatherClient = weatherClient;
        this.repository = repository;
    }

    public WeatherResponse getWeather(String city) {

        city = city.toLowerCase().trim();

        // 1. Buscar en cache
        WeatherCache cached = repository.findByCity(city).orElse(null);

        // 2. CACHE HIT (TTL válido)
        if (cached != null &&
                cached.getFetchedAt().isAfter(LocalDateTime.now().minusMinutes(10))) {
            System.out.println("⚡ CACHE HIT");
            WeatherResponse response = new WeatherResponse();
            CurrentWeather cw = new CurrentWeather();
            cw.setTemperature(cached.getTemperature());
            response.setCurrent_weather(cw);

            return response;
        }

        // 3. Intentar API
        try {
            System.out.println("🌐 API CALL");

            WeatherResponse response = weatherClient.getWeather(city);
            // 4. Guardar (update o insert)
            WeatherCache entityToSave = (cached != null) ? cached : new WeatherCache();

            entityToSave.setCity(city);
            entityToSave.setTemperature(response.getCurrent_weather().getTemperature());
            entityToSave.setFetchedAt(LocalDateTime.now());
            repository.save(entityToSave);
            return response;

        } catch (Exception e) {

            System.out.println("❌ API FAILED → fallback");

            // 5. Fallback → usar cache aunque esté expirado
            if (cached != null) {
                System.out.println("⚡ RETURNING OLD CACHE");
                WeatherResponse response = new WeatherResponse();
                CurrentWeather cw = new CurrentWeather();
                cw.setTemperature(cached.getTemperature());
                response.setCurrent_weather(cw);

                return response;
            }

            // 6. Nada disponible
            throw new RuntimeException("Weather service unavailable and no cache found");
        }
    }
}