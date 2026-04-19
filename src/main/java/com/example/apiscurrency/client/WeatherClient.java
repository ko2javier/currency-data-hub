package com.example.apiscurrency.client;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.apiscurrency.dto.GeoResponse;
import com.example.apiscurrency.dto.Result;
import com.example.apiscurrency.dto.WeatherResponse;

@Component
public class WeatherClient {

    private final RestTemplate restTemplate = new RestTemplate();

    public WeatherResponse getWeather(String city) {

        // 1 obtener coordenadas
        String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + city;

        GeoResponse geoResponse = restTemplate.getForObject(geoUrl, GeoResponse.class);

        if (geoResponse == null || geoResponse.getResults() == null || geoResponse.getResults().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciudad no encontrada");
        }

        Result firstResult = geoResponse.getResults().get(0);

        if (firstResult.getPopulation() != null && firstResult.getPopulation() < 1000) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciudad no encontrada");
        }

        double lat = firstResult.getLatitude();
        double lon = firstResult.getLongitude();

        // 2️⃣ llamar weather API con coords reales
        String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude="
                + lat + "&longitude=" + lon + "&current_weather=true";

        return restTemplate.getForObject(weatherUrl, WeatherResponse.class);
    }
}