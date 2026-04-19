package com.example.apiscurrency;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires live MySQL + Redis — run only in integration environment")
class ApisCurrencyApplicationTests {

    @Test
    void contextLoads() {
    }

}
