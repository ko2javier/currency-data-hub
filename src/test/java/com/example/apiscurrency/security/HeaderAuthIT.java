package com.example.apiscurrency.security;

import com.example.apiscurrency.dto.CurrentWeather;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.support.CurrencyIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La seguridad de este servicio, de punta a punta por HTTP: cadena de Spring
 * Security, HeaderAuthFilter, @PreAuthorize y los controladores.
 *
 * <p>Este servicio NO valida tokens. Quien los valida es el gateway, que despues
 * reenvia la identidad en dos cabeceras (X-User-Name y X-User-Roles), y
 * HeaderAuthFilter las convierte en la autenticacion de Spring. Es un diseno
 * valido <b>solo si nadie puede llegar a este servicio sin pasar por el
 * gateway</b>. Ver {@link #quien_llega_directo_se_declara_administrador_con_dos_cabeceras()}.
 */
class HeaderAuthIT extends CurrencyIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpio() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        jdbc.update("DELETE FROM weather_cache");
        jdbc.update("DELETE FROM currency_cache");
        reset(weatherClient, currencyClient, kafkaEventProducer);
    }

    private HttpEntity<Void> como(String usuario, String roles) {
        HttpHeaders h = new HttpHeaders();
        if (usuario != null) {
            h.set("X-User-Name", usuario);
        }
        if (roles != null) {
            h.set("X-User-Roles", roles);
        }
        return new HttpEntity<>(h);
    }

    private static WeatherResponse temperatura(double grados) {
        WeatherResponse r = new WeatherResponse();
        CurrentWeather cw = new CurrentWeather();
        cw.setTemperature(grados);
        r.setCurrent_weather(cw);
        return r;
    }

    // -------------------------------------------------------------------
    // Sin identidad
    // -------------------------------------------------------------------
    @Test
    void sin_cabeceras_de_identidad_se_rechaza() {
        ResponseEntity<String> r = http.exchange("/weather/madrid", HttpMethod.GET, como(null, null), String.class);

        // 403 y no 401: no hay ningun mecanismo de autenticacion configurado
        // (formLogin, httpBasic... no existen aqui), asi que Spring Security
        // responde con su Http403ForbiddenEntryPoint por defecto. Es lo que ve
        // quien llegue sin pasar por el gateway y sin cabeceras.
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void el_actuator_es_publico() {
        // Lo consulta Prometheus, que no tiene identidad.
        ResponseEntity<String> r = http.exchange("/actuator/health", HttpMethod.GET, como(null, null), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------------------------
    // Un USER normal
    // -------------------------------------------------------------------
    @Test
    void un_user_consulta_el_clima_y_se_publica_el_evento_con_su_nombre_y_su_ip() {
        when(weatherClient.getWeather("madrid")).thenReturn(temperatura(21.5));
        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Name", "jabier");
        h.set("X-User-Roles", "ROLE_USER");
        // Detras del gateway, getRemoteAddr() es la IP del gateway. La del
        // cliente viene en la primera entrada de X-Forwarded-For.
        h.set("X-Forwarded-For", "203.0.113.7, 10.0.0.1");

        ResponseEntity<String> r = http.exchange("/weather/madrid", HttpMethod.GET, new HttpEntity<>(h), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("21.5");
        // Es lo que alimenta el panel de eventos del Hub: quien consulto, desde
        // donde y que.
        verify(kafkaEventProducer).publishWeatherQuery("jabier", "203.0.113.7", "madrid");
    }

    @Test
    void un_user_consulta_una_divisa_y_se_publica_el_evento() {
        when(currencyClient.getRate("EUR", "USD")).thenReturn(1.08);

        ResponseEntity<String> r = http.exchange("/currency/EUR/USD", HttpMethod.GET,
                como("jabier", "ROLE_USER"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("1.08").contains("EUR").contains("USD");
        verify(kafkaEventProducer).publishCurrencyQuery(anyString(), anyString(), org.mockito.ArgumentMatchers.eq("EUR"),
                org.mockito.ArgumentMatchers.eq("USD"));
    }

    /**
     * CARACTERIZACION: fija lo que hace HOY, no lo que deberia.
     *
     * <p>Un acceso denegado por @PreAuthorize responde <b>503 Service
     * Unavailable</b>, no 403. La causa es GlobalExceptionHandler: captura
     * {@code RuntimeException} entera, y AccessDeniedException de Spring
     * Security ES una RuntimeException, asi que el "no tienes permiso" se
     * traduce a "el servicio no esta disponible" con el cuerpo
     * {"error": "Access Denied"}.
     *
     * <p>Consecuencias: un front que trate el 503 como caida mostraria "servicio
     * caido" a quien solo intento algo que no puede; y los 503 de Prometheus
     * mezclan errores de permisos con caidas reales. El arreglo es un
     * {@code @ExceptionHandler(AccessDeniedException.class)} que devuelva 403,
     * pero cambia el comportamiento de un servicio desplegado y no se hizo
     * aqui. El dia que se haga, estos dos tests se ponen rojos, que es lo que
     * se busca.
     */
    @Test
    void un_rol_que_no_es_user_ni_admin_NO_consulta_el_clima_pero_recibe_un_503() {
        // @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
        ResponseEntity<String> r = http.exchange("/weather/madrid", HttpMethod.GET,
                como("otro", "ROLE_INVITADO"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody()).contains("Access Denied");
        // Y lo importante: no llego a consultar nada. El permiso SI se aplica;
        // lo que esta mal es solo el codigo de estado.
        org.mockito.Mockito.verifyNoInteractions(weatherClient);
    }

    // -------------------------------------------------------------------
    // El endpoint de administrador
    // -------------------------------------------------------------------
    /** Mismo defecto que el test de arriba: el permiso se aplica, el codigo de estado es el equivocado. */
    @Test
    void un_user_NO_puede_usar_el_endpoint_de_administrador_pero_recibe_un_503() {
        ResponseEntity<String> r = http.exchange("/weather/cache/clear", HttpMethod.DELETE,
                como("jabier", "ROLE_USER"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody()).contains("Access Denied");
    }

    /**
     * CARACTERIZACION: fija lo que hace HOY, no lo que deberia.
     *
     * <p>El endpoint exige ADMIN y responde "Caché borrada por el
     * administrador." -- pero WeatherController.clearCache() solo devuelve ese
     * texto: no toca Redis ni la tabla. Lleva el resumen "Endpoint super
     * secreto" en el Swagger. Es un marcador de posicion que se ve como una
     * funcion. Queda apuntado como deuda; el dia que borre de verdad, este test
     * se pone rojo, que es lo que se busca.
     */
    @Test
    void el_endpoint_de_borrar_la_cache_dice_que_la_borra_pero_NO_borra_nada() {
        redis.opsForValue().set("weather:madrid", "21.5");
        jdbc.update("INSERT INTO weather_cache (city, temperature, fetched_at) VALUES ('madrid', 21.5, NOW())");

        ResponseEntity<String> r = http.exchange("/weather/cache/clear", HttpMethod.DELETE,
                como("admin", "ROLE_ADMIN"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo("Caché borrada por el administrador.");
        assertThat(redis.hasKey("weather:madrid")).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM weather_cache", Integer.class)).isEqualTo(1);
    }

    // -------------------------------------------------------------------
    // El agujero de este diseno
    // -------------------------------------------------------------------
    /**
     * CARACTERIZACION de una propiedad del diseno: fija lo que hace HOY.
     *
     * <p>HeaderAuthFilter acepta X-User-Name y X-User-Roles tal cual vienen: no
     * hay firma ni secreto compartido, y el servicio no puede saber si la
     * peticion paso por el gateway. La confianza no esta en el filtro sino en la
     * red: este diseno solo es seguro si el servicio es inalcanzable por
     * cualquier camino que no sea el gateway.
     *
     * <p>Que el test exista significa que el dia que se cambie (cabeceras
     * firmadas, o validar aqui el token), esto se pone rojo y alguien tiene que
     * mirar por que.
     */
    @Test
    void quien_llega_directo_se_declara_administrador_con_dos_cabeceras() {
        // Sin token, sin pasar por el gateway: solo dos cabeceras inventadas.
        ResponseEntity<String> r = http.exchange("/weather/cache/clear", HttpMethod.DELETE,
                como("cualquiera", "ROLE_ADMIN"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------------------------
    // Errores
    // -------------------------------------------------------------------
    @Test
    void si_la_api_cae_y_no_hay_cache_responde_503_con_el_mensaje() {
        when(weatherClient.getWeather(anyString())).thenThrow(new RuntimeException("Open-Meteo caido"));

        ResponseEntity<String> r = http.exchange("/weather/madrid", HttpMethod.GET,
                como("jabier", "ROLE_USER"), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody()).contains("Weather service unavailable and no cache found");
    }
}
