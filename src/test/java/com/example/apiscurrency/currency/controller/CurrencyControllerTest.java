package com.example.apiscurrency.currency.controller;

import com.example.apiscurrency.kafka.KafkaEventProducer;
import com.example.apiscurrency.currency.dto.CurrencyResponse;
import com.example.apiscurrency.currency.service.CurrencyService;
import com.example.apiscurrency.security.HeaderAuthFilter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CurrencyController.class)
@AutoConfigureMockMvc(addFilters = false)
class CurrencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrencyService service;

    @MockBean
    private HeaderAuthFilter headerAuthFilter;

    // FALTABA, y es lo que ponia en rojo los dos tests de esta clase desde
    // que el controlador empezo a publicar eventos: @WebMvcTest construye el
    // controlador de verdad, asi que necesita un doble de CADA dependencia
    // suya, y CurrencyController pide CurrencyService y KafkaEventProducer.
    // Sin esta linea: NoSuchBeanDefinitionException al cargar el contexto.
    @MockBean
    private KafkaEventProducer kafkaEventProducer;

    @Test
    @WithMockUser
    void shouldReturnRate() throws Exception {
        CurrencyResponse response = new CurrencyResponse();
        response.setFrom("EUR");
        response.setTo("USD");
        response.setRate(1.20);

        when(service.getRate("EUR", "USD")).thenReturn(response);

        mockMvc.perform(get("/currency/EUR/USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(1.20));
    }

    @Test
    @WithMockUser
    void shouldReturn500WhenServiceFails() throws Exception {
        when(service.getRate("EUR", "USD")).thenThrow(new RuntimeException("fail"));

        mockMvc.perform(get("/currency/EUR/USD"))
                .andExpect(status().isServiceUnavailable());
    }
}
