package com.example.apiscurrency.currency.service;

import com.example.apiscurrency.currency.client.CurrencyClient;
import com.example.apiscurrency.currency.dto.CurrencyResponse;
import com.example.apiscurrency.currency.model.CurrencyCache;
import com.example.apiscurrency.currency.repository.CurrencyRepository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CurrencyService {

    private final CurrencyRepository repository;
    private final CurrencyClient client;
    private final StringRedisTemplate redisTemplate;

    public CurrencyService(CurrencyRepository repository, CurrencyClient client, StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.client = client;
        this.redisTemplate = redisTemplate;
    }

    public CurrencyResponse getRate(String from, String to) {

        from = from.toUpperCase().trim();
        to = to.toUpperCase().trim();

        String key = "currency:" + from + ":" + to;

        // 1. REDIS
        String cachedRedis = redisTemplate.opsForValue().get(key);

        if (cachedRedis != null) {
            System.out.println("⚡ REDIS HIT");

            CurrencyResponse response = new CurrencyResponse();
            response.setFrom(from);
            response.setTo(to);
            response.setRate(Double.parseDouble(cachedRedis));

            return response;
        }

        // 2. DB
        CurrencyCache cached = repository.findByFromCurrencyAndToCurrency(from, to).orElse(null);

        if (cached != null &&
                cached.getFetchedAt().isAfter(LocalDateTime.now().minusMinutes(10))) {

            System.out.println("⚡ DB CACHE HIT");

            CurrencyResponse response = new CurrencyResponse();
            response.setFrom(from);
            response.setTo(to);
            response.setRate(cached.getRate());

            return response;
        }

        // 3. API
        try {
            System.out.println("🌐 API CALL");

            double rate = client.getRate(from, to);

            // 4. REDIS
            redisTemplate.opsForValue().set(
                    key,
                    String.valueOf(rate),
                    java.time.Duration.ofMinutes(10)
            );

            // 5. DB
            CurrencyCache entity = (cached != null) ? cached : new CurrencyCache();
            entity.setFromCurrency(from);
            entity.setToCurrency(to);
            entity.setRate(rate);
            entity.setFetchedAt(LocalDateTime.now());

            repository.save(entity);

            CurrencyResponse response = new CurrencyResponse();
            response.setFrom(from);
            response.setTo(to);
            response.setRate(rate);

            return response;

        } catch (Exception e) {

            System.out.println("❌ API FAILED → fallback");

            if (cached != null) {
                System.out.println("⚡ RETURNING OLD DB CACHE");

                CurrencyResponse response = new CurrencyResponse();
                response.setFrom(from);
                response.setTo(to);
                response.setRate(cached.getRate());

                return response;
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