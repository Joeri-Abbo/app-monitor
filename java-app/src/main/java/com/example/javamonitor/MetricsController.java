package com.example.javamonitor;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prometheus scrape endpoint.
 *
 * <p>Actuator already publishes the same registry at
 * {@code /actuator/prometheus}; this route mirrors the PHP app's {@code /metrics}
 * so both jobs in {@code prometheus.yml} look identical.
 */
@RestController
public class MetricsController {

    private static final String TEXT_FORMAT = "text/plain; version=0.0.4; charset=utf-8";

    private final PrometheusMeterRegistry registry;

    MetricsController(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(value = "/metrics", produces = TEXT_FORMAT)
    public String metrics() {
        return registry.scrape();
    }
}
