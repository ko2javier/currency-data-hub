package com.example.apiscurrency.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final KafkaTemplate<String, KafkaEvent> kafkaTemplate;

    @Value("${kafka.topics.currency-query}")
    private String topicCurrencyQuery;

    @Value("${kafka.topics.weather-query}")
    private String topicWeatherQuery;

    public void publishCurrencyQuery(String username, String clientIp, String from, String to) {
        KafkaEvent event = KafkaEvent.builder()
                .eventType("CURRENCY_QUERY")
                .username(username)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .metadata(Map.of("from", from, "to", to))
                .build();
        send(topicCurrencyQuery, username, event);
    }

    public void publishWeatherQuery(String username, String clientIp, String city) {
        KafkaEvent event = KafkaEvent.builder()
                .eventType("WEATHER_QUERY")
                .username(username)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .metadata(Map.of("city", city))
                .build();
        send(topicWeatherQuery, username, event);
    }

    private void send(String topic, String key, KafkaEvent event) {
        CompletableFuture<SendResult<String, KafkaEvent>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Error publicando evento {} en topic {}: {}",
                        event.getEventType(), topic, ex.getMessage());
            } else {
                log.debug("Evento {} publicado → topic={} partition={} offset={}",
                        event.getEventType(), topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
