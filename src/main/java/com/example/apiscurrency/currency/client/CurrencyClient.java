package com.example.apiscurrency.currency.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class CurrencyClient {

    private final RestTemplate restTemplate = new RestTemplate();

    public double getRate(String from, String to) {

        String url = "https://open.er-api.com/v6/latest/" + from;

        Map response = restTemplate.getForObject(url, Map.class);

        Map rates = (Map) response.get("rates");

        if (rates == null || !rates.containsKey(to)) {
            throw new RuntimeException("Invalid API response");
        }

        return Double.parseDouble(rates.get(to).toString());
    }
}