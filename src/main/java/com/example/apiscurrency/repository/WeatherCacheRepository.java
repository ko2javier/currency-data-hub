package com.example.apiscurrency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.apiscurrency.model.WeatherCache;

import java.util.Optional;

public interface WeatherCacheRepository extends JpaRepository<WeatherCache, Long> {

    Optional<WeatherCache> findByCity(String city);
}