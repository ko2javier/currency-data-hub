package com.example.apiscurrency.currency.service;

import com.example.apiscurrency.currency.dto.CurrencyResponse;
import com.example.apiscurrency.currency.repository.CurrencyRepository;
import com.example.apiscurrency.support.CurrencyIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La cache de tres niveles de CurrencyService (Redis -> tabla currency_cache ->
 * API de divisas, con la tabla como respaldo si la API cae), con Redis y MySQL
 * de verdad. Es la gemela de {@code WeatherCacheIT}; los comentarios largos
 * estan alli.
 *
 * <p>Lo propio de las divisas: la clave de cache lleva DOS monedas
 * (currency:EUR:USD) y la tabla tiene UNIQUE(fromCurrency, toCurrency), asi que
 * hay un caso que el clima no tiene: EUR->USD y USD->EUR son filas distintas.
 */
class CurrencyCacheIT extends CurrencyIntegrationTest {

    @Autowired
    private CurrencyService servicio;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CurrencyRepository tabla;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void todoVacio() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        jdbc.update("DELETE FROM currency_cache");
        reset(currencyClient);
    }

    @Test
    void la_primera_consulta_llama_a_la_api_y_escribe_en_redis_y_en_la_tabla() {
        when(currencyClient.getRate("EUR", "USD")).thenReturn(1.08);

        CurrencyResponse r = servicio.getRate("EUR", "USD");

        assertThat(r.getFrom()).isEqualTo("EUR");
        assertThat(r.getTo()).isEqualTo("USD");
        assertThat(r.getRate()).isEqualTo(1.08);
        assertThat(redis.opsForValue().get("currency:EUR:USD")).isEqualTo("1.08");
        assertThat(redis.getExpire("currency:EUR:USD")).isBetween(590L, 600L);
        assertThat(tabla.findByFromCurrencyAndToCurrency("EUR", "USD").orElseThrow().getRate()).isEqualTo(1.08);
    }

    @Test
    void la_segunda_consulta_sale_de_redis_sin_llamar_a_la_api() {
        when(currencyClient.getRate("EUR", "USD")).thenReturn(1.08);
        servicio.getRate("EUR", "USD");

        servicio.getRate("EUR", "USD");

        verify(currencyClient, times(1)).getRate("EUR", "USD");
    }

    @Test
    void sin_redis_responde_la_tabla_si_tiene_menos_de_diez_minutos() {
        when(currencyClient.getRate("EUR", "USD")).thenReturn(1.08);
        servicio.getRate("EUR", "USD");
        redis.delete("currency:EUR:USD");

        CurrencyResponse r = servicio.getRate("EUR", "USD");

        assertThat(r.getRate()).isEqualTo(1.08);
        verify(currencyClient, times(1)).getRate("EUR", "USD");
    }

    @Test
    void con_la_tabla_caducada_vuelve_a_la_api_y_ACTUALIZA_la_fila_sin_duplicarla() {
        when(currencyClient.getRate("EUR", "USD")).thenReturn(1.08);
        servicio.getRate("EUR", "USD");
        redis.delete("currency:EUR:USD");
        jdbc.update("UPDATE currency_cache SET fetched_at = ?", LocalDateTime.now().minusMinutes(11));
        when(currencyClient.getRate("EUR", "USD")).thenReturn(1.10);

        CurrencyResponse r = servicio.getRate("EUR", "USD");

        assertThat(r.getRate()).isEqualTo(1.10);
        // UNIQUE(fromCurrency, toCurrency): insertar en vez de reutilizar la
        // fila daria un error de clave duplicada.
        assertThat(tabla.count()).isEqualTo(1);
    }

    @Test
    void si_la_api_cae_se_devuelve_la_tabla_aunque_este_caducada() {
        when(currencyClient.getRate("EUR", "USD")).thenReturn(1.08);
        servicio.getRate("EUR", "USD");
        redis.delete("currency:EUR:USD");
        jdbc.update("UPDATE currency_cache SET fetched_at = ?", LocalDateTime.now().minusHours(5));
        when(currencyClient.getRate("EUR", "USD")).thenThrow(new RuntimeException("API caida"));

        CurrencyResponse r = servicio.getRate("EUR", "USD");

        assertThat(r.getRate()).isEqualTo(1.08);
    }

    @Test
    void si_la_api_cae_y_no_hay_nada_guardado_falla_sin_dejar_basura() {
        when(currencyClient.getRate(anyString(), anyString())).thenThrow(new RuntimeException("API caida"));

        assertThatThrownBy(() -> servicio.getRate("EUR", "USD"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Currency service unavailable and no cache found");
        assertThat(tabla.count()).isZero();
        assertThat(redis.hasKey("currency:EUR:USD")).isFalse();
    }

    @Test
    void eur_usd_y_usd_eur_son_dos_cambios_distintos_con_dos_filas() {
        when(currencyClient.getRate("EUR", "USD")).thenReturn(1.08);
        when(currencyClient.getRate("USD", "EUR")).thenReturn(0.93);

        servicio.getRate("EUR", "USD");
        CurrencyResponse inverso = servicio.getRate("USD", "EUR");

        assertThat(inverso.getRate()).isEqualTo(0.93);
        assertThat(tabla.count()).isEqualTo(2);
    }

    @Test
    void las_monedas_se_normalizan_a_mayusculas_y_sin_espacios() {
        when(currencyClient.getRate("EUR", "USD")).thenReturn(1.08);
        servicio.getRate("eur", "usd");

        servicio.getRate(" EUR ", "Usd");

        verify(currencyClient, times(1)).getRate("EUR", "USD");
        assertThat(tabla.count()).isEqualTo(1);
    }
}
