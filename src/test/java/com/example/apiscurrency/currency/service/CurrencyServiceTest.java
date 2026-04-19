package com.example.apiscurrency.currency.service;

import com.example.apiscurrency.currency.client.CurrencyClient;
import com.example.apiscurrency.currency.dto.CurrencyResponse;
import com.example.apiscurrency.currency.model.CurrencyCache;
import com.example.apiscurrency.currency.repository.CurrencyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrencyServiceTest {

    @Mock
    private CurrencyRepository repository;

    @Mock
    private CurrencyClient client;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private CurrencyService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // TEST 1 — DB CACHE HIT (Redis miss → DB fresco)
    @Test
    void shouldReturnCacheWhenValid() {
        when(valueOps.get("currency:EUR:USD")).thenReturn(null);

        CurrencyCache cache = new CurrencyCache();
        cache.setFromCurrency("EUR");
        cache.setToCurrency("USD");
        cache.setRate(1.10);
        cache.setFetchedAt(LocalDateTime.now());

        when(repository.findByFromCurrencyAndToCurrency("EUR", "USD")).thenReturn(Optional.of(cache));

        CurrencyResponse result = service.getRate("EUR", "USD");

        assertEquals(1.10, result.getRate());
        verify(client, never()).getRate(any(), any());
    }

    // TEST 2 — API CALL (Redis miss → DB expirado → API)
    @Test
    void shouldCallApiWhenCacheExpired() {
        when(valueOps.get("currency:EUR:USD")).thenReturn(null);

        CurrencyCache cache = new CurrencyCache();
        cache.setFromCurrency("EUR");
        cache.setToCurrency("USD");
        cache.setRate(1.00);
        cache.setFetchedAt(LocalDateTime.now().minusMinutes(20));

        when(repository.findByFromCurrencyAndToCurrency("EUR", "USD")).thenReturn(Optional.of(cache));
        when(client.getRate("EUR", "USD")).thenReturn(1.20);

        CurrencyResponse result = service.getRate("EUR", "USD");

        assertEquals(1.20, result.getRate());
        verify(client, times(1)).getRate("EUR", "USD");
    }

    // TEST 3 — FALLBACK (Redis miss → DB expirado → API falla → DB stale)
    @Test
    void shouldReturnOldCacheWhenApiFails() {
        when(valueOps.get("currency:EUR:USD")).thenReturn(null);

        CurrencyCache cache = new CurrencyCache();
        cache.setFromCurrency("EUR");
        cache.setToCurrency("USD");
        cache.setRate(1.05);
        cache.setFetchedAt(LocalDateTime.now().minusMinutes(20));

        when(repository.findByFromCurrencyAndToCurrency("EUR", "USD")).thenReturn(Optional.of(cache));
        when(client.getRate("EUR", "USD")).thenThrow(new RuntimeException("API DOWN"));

        CurrencyResponse result = service.getRate("EUR", "USD");

        assertEquals(1.05, result.getRate());
    }

    // TEST 4 — NO CACHE + API FAIL → excepción
    @Test
    void shouldThrowWhenNoCacheAndApiFails() {
        when(valueOps.get("currency:EUR:USD")).thenReturn(null);
        when(repository.findByFromCurrencyAndToCurrency("EUR", "USD")).thenReturn(Optional.empty());
        when(client.getRate("EUR", "USD")).thenThrow(new RuntimeException("API DOWN"));

        assertThrows(RuntimeException.class, () -> service.getRate("EUR", "USD"));
    }

    // TEST 5 — REDIS HIT (retorna directo sin tocar DB ni API)
    @Test
    void shouldReturnFromRedisWhenPresent() {
        when(valueOps.get("currency:EUR:USD")).thenReturn("1.08");

        CurrencyResponse result = service.getRate("EUR", "USD");

        assertEquals(1.08, result.getRate());
        verify(repository, never()).findByFromCurrencyAndToCurrency(any(), any());
        verify(client, never()).getRate(any(), any());
    }
}
