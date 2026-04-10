package com.example.apiscurrency.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.example.apiscurrency.dto.GeoResponse;
import com.example.apiscurrency.dto.WeatherResponse;

@Component
public class WeatherClient {

    private final RestTemplate restTemplate = new RestTemplate();

    public WeatherResponse getWeather(String city) {

        // 1 obtener coordenadas
        String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + city;

        GeoResponse geoResponse = restTemplate.getForObject(geoUrl, GeoResponse.class);

        double lat = geoResponse.getResults().get(0).getLatitude();
        double lon = geoResponse.getResults().get(0).getLongitude();

        // 2️⃣ llamar weather API con coords reales
        String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude="
                + lat + "&longitude=" + lon + "&current_weather=true";

        return restTemplate.getForObject(weatherUrl, WeatherResponse.class);
    }
}