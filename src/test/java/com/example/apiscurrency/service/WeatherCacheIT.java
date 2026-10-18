package com.example.apiscurrency.service;

import com.example.apiscurrency.dto.CurrentWeather;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.model.WeatherCache;
import com.example.apiscurrency.repository.WeatherCacheRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La cache de tres niveles de WeatherService, con Redis y MySQL de verdad:
 *
 * <pre>
 *   1. Redis            (TTL 10 min)   weather:{ciudad}
 *   2. Tabla weather_cache             vale si se escribio hace menos de 10 min
 *   3. Open-Meteo                      si cae, se devuelve la tabla aunque este caducada
 * </pre>
 *
 * <p>WeatherServiceTest ya comprueba la logica de decision con Mockito. Lo que
 * este fichero anade es lo que un mock no puede demostrar: que lo que se
 * escribe en un nivel se LEE del siguiente, que Redis guarda de verdad un TTL, y
 * que la actualizacion de una fila existente aguanta el indice UNIQUE de city.
 *
 * <p>Se llama al servicio directamente, sin HTTP: la seguridad y los codigos de
 * estado ya los cubre {@code HeaderAuthIT}.
 */
class WeatherCacheIT extends CurrencyIntegrationTest {

    private static final String CIUDAD = "madrid";
    private static final String CLAVE_REDIS = "weather:madrid";

    @Autowired
    private WeatherService servicio;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private WeatherCacheRepository tabla;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void todoVacio() {
        // Redis es compartido por todos los tests de la JVM, y la tabla tambien:
        // sin esto, el orden de ejecucion decidiria el resultado.
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        jdbc.update("DELETE FROM weather_cache");
        reset(weatherClient);
    }

    private static WeatherResponse temperatura(double grados) {
        WeatherResponse r = new WeatherResponse();
        CurrentWeather cw = new CurrentWeather();
        cw.setTemperature(grados);
        r.setCurrent_weather(cw);
        return r;
    }

    // -------------------------------------------------------------------
    // La primera consulta escribe en los dos niveles
    // -------------------------------------------------------------------
    @Test
    void la_primera_consulta_llama_a_la_api_y_escribe_en_redis_y_en_la_tabla() {
        when(weatherClient.getWeather(CIUDAD)).thenReturn(temperatura(21.5));

        WeatherResponse r = servicio.getWeather(CIUDAD);

        assertThat(r.getCurrent_weather().getTemperature()).isEqualTo(21.5);
        verify(weatherClient, times(1)).getWeather(CIUDAD);
        // Redis, con su TTL: 10 minutos = 600 s. Lo que importa es que SEA un TTL;
        // una clave sin caducidad se quedaria con la temperatura de hoy para siempre.
        assertThat(redis.opsForValue().get(CLAVE_REDIS)).isEqualTo("21.5");
        assertThat(redis.getExpire(CLAVE_REDIS)).isBetween(590L, 600L);
        // La tabla.
        WeatherCache fila = tabla.findByCity(CIUDAD).orElseThrow();
        assertThat(fila.getTemperature()).isEqualTo(21.5);
        assertThat(fila.getFetchedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void la_segunda_consulta_sale_de_redis_sin_llamar_a_la_api() {
        when(weatherClient.getWeather(CIUDAD)).thenReturn(temperatura(21.5));
        servicio.getWeather(CIUDAD);

        WeatherResponse segunda = servicio.getWeather(CIUDAD);

        assertThat(segunda.getCurrent_weather().getTemperature()).isEqualTo(21.5);
        // Una sola llamada en total: la de la primera consulta.
        verify(weatherClient, times(1)).getWeather(CIUDAD);
    }

    // -------------------------------------------------------------------
    // Redis vacio: la tabla responde
    // -------------------------------------------------------------------
    @Test
    void sin_redis_responde_la_tabla_si_tiene_menos_de_diez_minutos() {
        when(weatherClient.getWeather(CIUDAD)).thenReturn(temperatura(21.5));
        servicio.getWeather(CIUDAD);
        // Redis pierde la clave (reinicio, desalojo, TTL cumplido...).
        redis.delete(CLAVE_REDIS);

        WeatherResponse r = servicio.getWeather(CIUDAD);

        assertThat(r.getCurrent_weather().getTemperature()).isEqualTo(21.5);
        verify(weatherClient, times(1)).getWeather(CIUDAD);
    }

    @Test
    void con_la_tabla_caducada_vuelve_a_la_api_y_ACTUALIZA_la_fila_sin_duplicarla() {
        when(weatherClient.getWeather(CIUDAD)).thenReturn(temperatura(21.5));
        servicio.getWeather(CIUDAD);
        redis.delete(CLAVE_REDIS);
        // La fila tiene ahora 11 minutos: pasa el limite de 10.
        jdbc.update("UPDATE weather_cache SET fetched_at = ?", LocalDateTime.now().minusMinutes(11));
        when(weatherClient.getWeather(CIUDAD)).thenReturn(temperatura(30.0));

        WeatherResponse r = servicio.getWeather(CIUDAD);

        assertThat(r.getCurrent_weather().getTemperature()).isEqualTo(30.0);
        verify(weatherClient, times(2)).getWeather(CIUDAD);
        // Esto es lo que ningun mock del repositorio puede decir: weather_cache
        // tiene UNIQUE(city). Si el servicio insertara en vez de reutilizar la
        // fila, MySQL lo rechazaria con un error de clave duplicada.
        assertThat(tabla.count()).isEqualTo(1);
        assertThat(tabla.findByCity(CIUDAD).orElseThrow().getTemperature()).isEqualTo(30.0);
    }

    // -------------------------------------------------------------------
    // La API cae: el respaldo
    // -------------------------------------------------------------------
    @Test
    void si_la_api_cae_se_devuelve_la_tabla_aunque_este_caducada() {
        when(weatherClient.getWeather(CIUDAD)).thenReturn(temperatura(21.5));
        servicio.getWeather(CIUDAD);
        redis.delete(CLAVE_REDIS);
        jdbc.update("UPDATE weather_cache SET fetched_at = ?", LocalDateTime.now().minusHours(5));
        when(weatherClient.getWeather(CIUDAD)).thenThrow(new RuntimeException("Open-Meteo caido"));

        WeatherResponse r = servicio.getWeather(CIUDAD);

        // Un dato de hace cinco horas antes que un error: es la decision de diseno.
        assertThat(r.getCurrent_weather().getTemperature()).isEqualTo(21.5);
    }

    @Test
    void si_la_api_cae_y_no_hay_nada_guardado_falla() {
        when(weatherClient.getWeather(anyString())).thenThrow(new RuntimeException("Open-Meteo caido"));

        assertThatThrownBy(() -> servicio.getWeather(CIUDAD))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Weather service unavailable and no cache found");
        // Y no se guarda nada a medias.
        assertThat(tabla.count()).isZero();
        assertThat(redis.hasKey(CLAVE_REDIS)).isFalse();
    }

    // -------------------------------------------------------------------
    // La clave de cache normaliza la ciudad
    // -------------------------------------------------------------------
    @Test
    void madrid_y_MADRID_con_espacios_comparten_la_misma_cache() {
        when(weatherClient.getWeather(CIUDAD)).thenReturn(temperatura(21.5));
        servicio.getWeather("Madrid");

        servicio.getWeather("  MADRID ");

        // Si no se normalizara, serian tres claves y tres llamadas a la API.
        verify(weatherClient, times(1)).getWeather(CIUDAD);
        assertThat(tabla.count()).isEqualTo(1);
    }

    @Test
    void una_consulta_con_redis_lleno_no_toca_la_api() {
        redis.opsForValue().set(CLAVE_REDIS, "7.0");

        WeatherResponse r = servicio.getWeather(CIUDAD);

        assertThat(r.getCurrent_weather().getTemperature()).isEqualTo(7.0);
        verifyNoInteractions(weatherClient);
    }
}
