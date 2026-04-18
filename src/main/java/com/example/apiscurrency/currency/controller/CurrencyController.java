package com.example.apiscurrency.currency.controller;

import com.example.apiscurrency.currency.dto.CurrencyResponse;
import com.example.apiscurrency.currency.service.CurrencyService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/currency")
public class CurrencyController {

    private final CurrencyService service;

    public CurrencyController(CurrencyService service) {
        this.service = service;
    }

    @GetMapping("/rates")
    public List<String> getAvailableCurrencies() {
        return service.getAvailableCurrencies();
    }

    @GetMapping("/{from}/{to}")
    public CurrencyResponse getRate(
            @PathVariable String from,
            @PathVariable String to) {

        return service.getRate(from, to);
    }
}