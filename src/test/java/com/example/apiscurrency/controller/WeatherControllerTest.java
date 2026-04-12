package com.example.apiscurrency.controller;

import com.example.apiscurrency.dto.CurrentWeather;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.service.WeatherService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WeatherController.class)
class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WeatherService weatherService;

    @Test
    void shouldReturnWeather() throws Exception {

        // 🔹 mock respuesta del service
        WeatherResponse response = new WeatherResponse();
        CurrentWeather cw = new CurrentWeather();
        cw.setTemperature(25);
        response.setCurrent_weather(cw);

        when(weatherService.getWeather("madrid"))
                .thenReturn(response);

        // 🔹 llamada HTTP simulada
        mockMvc.perform(get("/weather/madrid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_weather.temperature").value(25));
    }
    @Test
    void shouldReturnErrorWhenServiceFails() throws Exception {

        when(weatherService.getWeather("madrid"))
                .thenThrow(new RuntimeException("fail"));

        mockMvc.perform(get("/weather/madrid"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("fail"));
    }
}