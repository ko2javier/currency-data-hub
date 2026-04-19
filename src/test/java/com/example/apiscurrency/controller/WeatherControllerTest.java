package com.example.apiscurrency.controller;

import com.example.apiscurrency.dto.CurrentWeather;
import com.example.apiscurrency.dto.WeatherResponse;
import com.example.apiscurrency.security.HeaderAuthFilter;
import com.example.apiscurrency.service.WeatherService;

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

@WebMvcTest(WeatherController.class)
@AutoConfigureMockMvc(addFilters = false)
class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WeatherService weatherService;

    @MockBean
    private HeaderAuthFilter headerAuthFilter;

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturnWeather() throws Exception {
        WeatherResponse response = new WeatherResponse();
        CurrentWeather cw = new CurrentWeather();
        cw.setTemperature(25);
        response.setCurrent_weather(cw);

        when(weatherService.getWeather("madrid")).thenReturn(response);

        mockMvc.perform(get("/weather/madrid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_weather.temperature").value(25));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldReturn500WhenServiceFails() throws Exception {
        when(weatherService.getWeather("madrid")).thenThrow(new RuntimeException("fail"));

        mockMvc.perform(get("/weather/madrid"))
                .andExpect(status().isServiceUnavailable());
    }
}
