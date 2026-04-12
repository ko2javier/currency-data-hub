package com.example.apiscurrency.currency.repository;

import com.example.apiscurrency.currency.model.CurrencyCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<CurrencyCache, Long> {

    Optional<CurrencyCache> findByFromCurrencyAndToCurrency(String from, String to);
}