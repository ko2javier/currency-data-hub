package com.example.apiscurrency.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import com.example.apiscurrency.client.WeatherClient;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.dto.CurrentWeather;
import com.example.apiscurrency.model.WeatherCache;
import com.example.apiscurrency.repository.WeatherCacheRepository;

@Service
public class WeatherService {


    private final StringRedisTemplate redisTemplate;
    private final WeatherClient weatherClient;
    private final WeatherCacheRepository repository;

    public WeatherService(WeatherClient weatherClient, WeatherCacheRepository repository,
                          StringRedisTemplate redisTemplate            ) {
        this.weatherClient = weatherClient;
        this.repository = repository;
        this.redisTemplate= redisTemplate;
    }

    public WeatherResponse getWeather(String city) {

        city = city.toLowerCase().trim();

        String key = "weather:" + city;

        // 1. REDIS
        String cachedRedis = redisTemplate.opsForValue().get(key);

        if (cachedRedis != null) {
            System.out.println("⚡ REDIS HIT");

            WeatherResponse response = new WeatherResponse();
            CurrentWeather cw = new CurrentWeather();
            cw.setTemperature(Double.parseDouble(cachedRedis));
            response.setCurrent_weather(cw);

            return response;
        }

        // 2. DB (fallback cache)
        WeatherCache cached = repository.findByCity(city).orElse(null);

        if (cached != null &&
                cached.getFetchedAt().isAfter(LocalDateTime.now().minusMinutes(10))) {

            System.out.println("⚡ DB CACHE HIT");

            WeatherResponse response = new WeatherResponse();
            CurrentWeather cw = new CurrentWeather();
            cw.setTemperature(cached.getTemperature());
            response.setCurrent_weather(cw);

            return response;
        }

        // 3. API
        try {
            System.out.println("🌐 API CALL");

            WeatherResponse response = weatherClient.getWeather(city);

            double temp = response.getCurrent_weather().getTemperature();

            // 4. GUARDAR EN REDIS (TTL 10 min)
            redisTemplate.opsForValue().set(
                    key,
                    String.valueOf(temp),
                    java.time.Duration.ofMinutes(10)
            );

            // 5. GUARDAR EN DB
            WeatherCache entity = (cached != null) ? cached : new WeatherCache();
            entity.setCity(city);
            entity.setTemperature(temp);
            entity.setFetchedAt(LocalDateTime.now());

            repository.save(entity);

            return response;

        } catch (Exception e) {

            System.out.println("❌ API FAILED → fallback");

            // 6. fallback DB aunque esté expirado
            if (cached != null) {
                System.out.println("⚡ RETURNING OLD DB CACHE");

                WeatherResponse response = new WeatherResponse();
                CurrentWeather cw = new CurrentWeather();
                cw.setTemperature(cached.getTemperature());
                response.setCurrent_weather(cw);

                return response;
            }

            throw new RuntimeException("Weather service unavailable and no cache found");
        }
    }
}