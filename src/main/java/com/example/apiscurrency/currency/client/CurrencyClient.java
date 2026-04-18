package com.example.apiscurrency.currency.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    @SuppressWarnings("unchecked")
    public List<String> getAvailableCurrencies() {

        String url = "https://open.er-api.com/v6/latest/EUR";

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        if (response == null) {
            throw new RuntimeException("Empty response from exchange rate API");
        }

        Map<String, Object> rates = (Map<String, Object>) response.get("rates");

        if (rates == null) {
            throw new RuntimeException("Invalid API response: no rates field");
        }

        List<String> currencies = new ArrayList<>(rates.keySet());
        currencies.add("EUR"); // base currency is not included in rates
        Collections.sort(currencies);

        return currencies;
    }
}