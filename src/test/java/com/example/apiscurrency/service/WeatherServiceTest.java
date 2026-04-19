package com.example.apiscurrency.service;

import com.example.apiscurrency.client.WeatherClient;
import com.example.apiscurrency.dto.CurrentWeather;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.model.WeatherCache;
import com.example.apiscurrency.repository.WeatherCacheRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeatherServiceTest {

    @Mock
    private WeatherClient weatherClient;

    @Mock
    private WeatherCacheRepository repository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private WeatherService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // TEST 1 — DB CACHE HIT (Redis miss → DB fresco)
    @Test
    void shouldReturnCacheWhenValid() {
        when(valueOps.get("weather:madrid")).thenReturn(null);

        WeatherCache cache = new WeatherCache();
        cache.setCity("madrid");
        cache.setTemperature(25);
        cache.setFetchedAt(LocalDateTime.now());

        when(repository.findByCity("madrid")).thenReturn(Optional.of(cache));

        WeatherResponse result = service.getWeather("madrid");

        assertEquals(25, result.getCurrent_weather().getTemperature());
        verify(weatherClient, never()).getWeather(any());
    }

    // TEST 2 — API CALL (Redis miss → DB expirado → API)
    @Test
    void shouldCallApiWhenCacheExpired() {
        when(valueOps.get("weather:madrid")).thenReturn(null);

        WeatherCache cache = new WeatherCache();
        cache.setCity("madrid");
        cache.setTemperature(20);
        cache.setFetchedAt(LocalDateTime.now().minusMinutes(20));

        when(repository.findByCity("madrid")).thenReturn(Optional.of(cache));

        WeatherResponse apiResponse = new WeatherResponse();
        CurrentWeather cw = new CurrentWeather();
        cw.setTemperature(30);
        apiResponse.setCurrent_weather(cw);

        when(weatherClient.getWeather("madrid")).thenReturn(apiResponse);

        WeatherResponse result = service.getWeather("madrid");

        assertEquals(30, result.getCurrent_weather().getTemperature());
        verify(weatherClient, times(1)).getWeather("madrid");
    }

    // TEST 3 — FALLBACK (Redis miss → DB expirado → API falla → DB stale)
    @Test
    void shouldReturnOldCacheWhenApiFails() {
        when(valueOps.get("weather:madrid")).thenReturn(null);

        WeatherCache cache = new WeatherCache();
        cache.setCity("madrid");
        cache.setTemperature(18);
        cache.setFetchedAt(LocalDateTime.now().minusMinutes(20));

        when(repository.findByCity("madrid")).thenReturn(Optional.of(cache));
        when(weatherClient.getWeather("madrid")).thenThrow(new RuntimeException("API DOWN"));

        WeatherResponse result = service.getWeather("madrid");

        assertEquals(18, result.getCurrent_weather().getTemperature());
    }

    // TEST 4 — REDIS HIT (retorna directo sin tocar DB ni API)
    @Test
    void shouldReturnFromRedisWhenPresent() {
        when(valueOps.get("weather:madrid")).thenReturn("22.5");

        WeatherResponse result = service.getWeather("madrid");

        assertEquals(22.5, result.getCurrent_weather().getTemperature());
        verify(repository, never()).findByCity(any());
        verify(weatherClient, never()).getWeather(any());
    }

    // TEST 5 — CIUDAD NO ENCONTRADA (WeatherClient lanza 404 → se propaga)
    @Test
    void shouldThrowNotFoundWhenCityDoesNotExist() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(repository.findByCity("xyz123")).thenReturn(Optional.empty());
        when(weatherClient.getWeather("xyz123"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciudad no encontrada"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getWeather("xyz123"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
