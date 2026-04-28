package com.example.apiscurrency.currency.controller;

import com.example.apiscurrency.currency.dto.CurrencyResponse;
import com.example.apiscurrency.currency.service.CurrencyService;
import com.example.apiscurrency.kafka.KafkaEventProducer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/currency")
public class CurrencyController {

    private final CurrencyService service;
    private final KafkaEventProducer kafkaEventProducer;

    public CurrencyController(CurrencyService service, KafkaEventProducer kafkaEventProducer) {
        this.service = service;
        this.kafkaEventProducer = kafkaEventProducer;
    }

    @GetMapping("/rates")
    public List<String> getAvailableCurrencies() {
        return service.getAvailableCurrencies();
    }

    @GetMapping("/{from}/{to}")
    public CurrencyResponse getRate(
            @PathVariable String from,
            @PathVariable String to,
            Authentication authentication,
            HttpServletRequest request) {

        String username = authentication != null ? authentication.getName() : "anonymous";
        String clientIp = resolveClientIp(request);
        kafkaEventProducer.publishCurrencyQuery(username, clientIp, from, to);

        return service.getRate(from, to);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}