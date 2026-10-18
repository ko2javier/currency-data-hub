package com.example.apiscurrency.support;

import com.example.apiscurrency.client.WeatherClient;
import com.example.apiscurrency.currency.client.CurrencyClient;
import com.example.apiscurrency.kafka.KafkaEventProducer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de los tests de integracion del servicio de clima y divisas.
 *
 * <p>Esta aplicacion tiene una cache de TRES niveles (Redis -> tabla de MySQL ->
 * API externa, con la tabla como respaldo si la API cae), y esa es la pieza que
 * un mock no puede demostrar: WeatherServiceTest y CurrencyServiceTest ya
 * comprueban la logica con Mockito, pero con Redis y la tabla sustituidos no
 * se ve que lo que se escribe en uno se lee del otro, ni que el indice UNIQUE
 * de la tabla aguanta una actualizacion.
 *
 * <p><b>MySQL y Redis son de verdad, en contenedores.</b> Este servicio va con
 * ddl-auto=update, asi que Hibernate crea las tablas solo: no hace falta un
 * schema.sql.
 *
 * <p><b>Lo unico que se sustituye es lo que sale de la maquina:</b> los dos
 * clientes HTTP (Open-Meteo y la API de divisas) y el productor de Kafka. Un
 * test que llame a una API de terceros falla cuando esa API tiene un mal dia, y
 * eso no dice nada del codigo. Kafka no se levanta porque este servicio solo
 * produce (no hay ningun @KafkaListener); sin broker, el productor de verdad se
 * quedaria bloqueado esperando metadatos en cada peticion.
 *
 * <p><b>Los contenedores arrancan en un bloque static y no con
 * {@code @Container}</b>: con la anotacion los arranca la extension de
 * Testcontainers, y su orden frente a la de Spring no esta garantizado. Si Spring
 * crea el contexto primero, {@link DynamicPropertySource} lee un puerto que
 * todavia no existe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class CurrencyIntegrationTest {

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @MockBean
    protected KafkaEventProducer kafkaEventProducer;

    /** Lo que habla con Open-Meteo. Se sustituye: es una API de terceros. */
    @MockBean
    protected WeatherClient weatherClient;

    /** Lo que habla con la API de divisas. Se sustituye por lo mismo. */
    @MockBean
    protected CurrencyClient currencyClient;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("DB_URL", MYSQL::getJdbcUrl);
        registro.add("DB_USERNAME", MYSQL::getUsername);
        registro.add("DB_PASSWORD", MYSQL::getPassword);
        registro.add("REDIS_URL", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort());
        // Solo lo usa springdoc para pintar la URL del servidor en el Swagger.
        registro.add("GATEWAY_URL", () -> "http://localhost");
    }
}
