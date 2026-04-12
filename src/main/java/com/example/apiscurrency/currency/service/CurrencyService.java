package com.example.apiscurrency.currency.service;

import com.example.apiscurrency.currency.client.CurrencyClient;
import com.example.apiscurrency.currency.dto.CurrencyResponse;
import com.example.apiscurrency.currency.model.CurrencyCache;
import com.example.apiscurrency.currency.repository.CurrencyRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CurrencyService {

    private final CurrencyRepository repository;
    private final CurrencyClient client;

    public CurrencyService(CurrencyRepository repository, CurrencyClient client) {
        this.repository = repository;
        this.client = client;
    }

    public CurrencyResponse getRate(String from, String to) {

        from = from.toUpperCase().trim();
        to = to.toUpperCase().trim();

        CurrencyCache cached = repository
                .findByFromCurrencyAndToCurrency(from, to)
                .orElse(null);

        // 🔹 CACHE HIT (TTL 10 min)
        if (cached != null &&
                cached.getFetchedAt().isAfter(LocalDateTime.now().minusMinutes(10))) {

            System.out.println("⚡ CURRENCY CACHE HIT");

            return buildResponse(from, to, cached.getRate());
        }

        // 🔹 API CALL
        try {
            System.out.println("🌐 CURRENCY API CALL");

            double rate = client.getRate(from, to);

            CurrencyCache entity = (cached != null) ? cached : new CurrencyCache();

            entity.setFromCurrency(from);
            entity.setToCurrency(to);
            entity.setRate(rate);
            entity.setFetchedAt(LocalDateTime.now());

            repository.save(entity);

            return buildResponse(from, to, rate);

        } catch (Exception e) {

            System.out.println("❌ CURRENCY API FAILED → fallback");

            if (cached != null) {
                System.out.println("⚡ RETURNING OLD CURRENCY CACHE");
                return buildResponse(from, to, cached.getRate());
            }

            throw new RuntimeException("Currency service unavailable and no cache found");
        }
    }

    private CurrencyResponse buildResponse(String from, String to, double rate) {
        CurrencyResponse response = new CurrencyResponse();
        response.setFrom(from);
        response.setTo(to);
        response.setRate(rate);
        return response;
    }
}