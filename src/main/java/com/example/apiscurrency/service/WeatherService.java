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

        // 1. Buscar en cache
        WeatherCache cached = repository.findByCity(city).orElse(null);

        if (cached != null &&
                cached.getFetchedAt().isAfter(LocalDateTime.now().minusMinutes(10))) {

            System.out.println("CACHE HIT");

            WeatherResponse response = new WeatherResponse();
            CurrentWeather cw = new CurrentWeather();
            cw.setTemperature(cached.getTemperature());
            response.setCurrent_weather(cw);

            return response;
        }

        // 3. API externa
        System.out.println("API CALL");

        WeatherResponse response = weatherClient.getWeather(city);

        // 4. Guardar en DB (¡Aquí está el truco!)
        // Si 'cached' no es null, usamos ese mismo objeto. Si es null, creamos uno nuevo.
        WeatherCache entityToSave = (cached != null) ? cached : new WeatherCache();

        // 4. Guardar en DB
        entityToSave.setCity(city);
        entityToSave.setTemperature(response.getCurrent_weather().getTemperature());
        entityToSave.setFetchedAt(LocalDateTime.now());

        repository.save(entityToSave); // Si tenía ID, hará UPDATE. Si no, INSERT.

        return response;
    }
}