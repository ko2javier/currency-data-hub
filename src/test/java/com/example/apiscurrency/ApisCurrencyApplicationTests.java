package com.example.apiscurrency;

import com.example.apiscurrency.support.CurrencyIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Estaba deshabilitado con @Disabled("Requires live MySQL + Redis — run only in
 * integration environment"). Era cierto: application.properties pide la base
 * de datos y Redis sin valor por defecto. Ahora los dos salen de contenedores
 * (Testcontainers) en {@link CurrencyIntegrationTest}, asi que ya no hace falta
 * un entorno aparte para comprobar que el contexto arranca.
 */
class ApisCurrencyApplicationTests extends CurrencyIntegrationTest {

    @Test
    void contextLoads() {
    }
}
