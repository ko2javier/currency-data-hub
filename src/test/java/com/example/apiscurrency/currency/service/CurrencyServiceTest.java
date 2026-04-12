package com.example.apiscurrency.currency.service;

import com.example.apiscurrency.currency.client.CurrencyClient;
import com.example.apiscurrency.currency.dto.CurrencyResponse;
import com.example.apiscurrency.currency.model.CurrencyCache;
import com.example.apiscurrency.currency.repository.CurrencyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrencyServiceTest {

    @Mock
    private CurrencyRepository repository;

    @Mock
    private CurrencyClient client;

    @InjectMocks
    private CurrencyService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    // 🔥 TEST 1 — CACHE HIT
    @Test
    void shouldReturnCacheWhenValid() {

        CurrencyCache cache = new CurrencyCache();
        cache.setFromCurrency("EUR");
        cache.setToCurrency("USD");
        cache.setRate(1.10);
        cache.setFetchedAt(LocalDateTime.now());

        when(repository.findByFromCurrencyAndToCurrency("EUR", "USD"))
                .thenReturn(Optional.of(cache));

        CurrencyResponse result = service.getRate("EUR", "USD");

        assertEquals(1.10, result.getRate());
        verify(client, never()).getRate(any(), any());
    }

    // 🔥 TEST 2 — API CALL (cache expirado)
    @Test
    void shouldCallApiWhenCacheExpired() {

        CurrencyCache cache = new CurrencyCache();
        cache.setFromCurrency("EUR");
        cache.setToCurrency("USD");
        cache.setRate(1.00);
        cache.setFetchedAt(LocalDateTime.now().minusMinutes(20));

        when(repository.findByFromCurrencyAndToCurrency("EUR", "USD"))
                .thenReturn(Optional.of(cache));

        when(client.getRate("EUR", "USD"))
                .thenReturn(1.20);

        CurrencyResponse result = service.getRate("EUR", "USD");

        assertEquals(1.20, result.getRate());
        verify(client, times(1)).getRate("EUR", "USD");
    }

    // 🔥 TEST 3 — FALLBACK
    @Test
    void shouldReturnOldCacheWhenApiFails() {

        CurrencyCache cache = new CurrencyCache();
        cache.setFromCurrency("EUR");
        cache.setToCurrency("USD");
        cache.setRate(1.05);
        cache.setFetchedAt(LocalDateTime.now().minusMinutes(20));

        when(repository.findByFromCurrencyAndToCurrency("EUR", "USD"))
                .thenReturn(Optional.of(cache));

        when(client.getRate("EUR", "USD"))
                .thenThrow(new RuntimeException("API DOWN"));

        CurrencyResponse result = service.getRate("EUR", "USD");

        assertEquals(1.05, result.getRate());
    }

    // 🔥 TEST 4 — NO CACHE + API FAIL
    @Test
    void shouldThrowWhenNoCacheAndApiFails() {

        when(repository.findByFromCurrencyAndToCurrency("EUR", "USD"))
                .thenReturn(Optional.empty());

        when(client.getRate("EUR", "USD"))
                .thenThrow(new RuntimeException("API DOWN"));

        assertThrows(RuntimeException.class, () ->
                service.getRate("EUR", "USD"));
    }
}