package com.example.apiscurrency.service;

import com.example.apiscurrency.client.WeatherClient;
import com.example.apiscurrency.dto.CurrentWeather;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.model.WeatherCache;
import com.example.apiscurrency.repository.WeatherCacheRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeatherServiceTest {

    @Mock
    private WeatherClient weatherClient;

    @Mock
    private WeatherCacheRepository repository;

    @InjectMocks
    private WeatherService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    // 🔥 TEST 1 — CACHE HIT
    @Test
    void shouldReturnCacheWhenValid() {

        WeatherCache cache = new WeatherCache();
        cache.setCity("madrid");
        cache.setTemperature(25);
        cache.setFetchedAt(LocalDateTime.now());

        when(repository.findByCity("madrid"))
                .thenReturn(Optional.of(cache));

        WeatherResponse result = service.getWeather("madrid");

        assertEquals(25, result.getCurrent_weather().getTemperature());
        verify(weatherClient, never()).getWeather(any());
    }

    // 🔥 TEST 2 — API CALL
    @Test
    void shouldCallApiWhenCacheExpired() {

        WeatherCache cache = new WeatherCache();
        cache.setCity("madrid");
        cache.setTemperature(20);
        cache.setFetchedAt(LocalDateTime.now().minusMinutes(20));

        when(repository.findByCity("madrid"))
                .thenReturn(Optional.of(cache));

        WeatherResponse apiResponse = new WeatherResponse();
        CurrentWeather cw = new CurrentWeather();
        cw.setTemperature(30);
        apiResponse.setCurrent_weather(cw);

        when(weatherClient.getWeather("madrid"))
                .thenReturn(apiResponse);

        WeatherResponse result = service.getWeather("madrid");

        assertEquals(30, result.getCurrent_weather().getTemperature());
        verify(weatherClient, times(1)).getWeather("madrid");
    }

    // 🔥 TEST 3 — FALLBACK
    @Test
    void shouldReturnOldCacheWhenApiFails() {

        WeatherCache cache = new WeatherCache();
        cache.setCity("madrid");
        cache.setTemperature(18);
        cache.setFetchedAt(LocalDateTime.now().minusMinutes(20));

        when(repository.findByCity("madrid"))
                .thenReturn(Optional.of(cache));

        when(weatherClient.getWeather("madrid"))
                .thenThrow(new RuntimeException("API DOWN"));

        WeatherResponse result = service.getWeather("madrid");

        assertEquals(18, result.getCurrent_weather().getTemperature());
    }
}