package com.example.javamonitor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// @SpringBootTest swaps the Prometheus registry for the simple one unless
// observability is explicitly switched back on.
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JavaMonitorApplicationTests {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void homepageListsTheOtherRoutes() {
        ResponseEntity<String> response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("/javainfo", "/metrics", "Live resource usage");
    }

    @Test
    void javaInfoReportsTheRuntime() {
        ResponseEntity<String> response = rest.getForEntity("/javainfo", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Java runtime", "Garbage collectors", "System properties");
    }

    @Test
    void metricsExposeTheAppsOwnFamilies() {
        rest.getForEntity("/javainfo", String.class);

        ResponseEntity<String> response = rest.getForEntity("/metrics", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("javaapp_process_heap_used_bytes")
                .contains("javaapp_system_cpu_count")
                .contains("javaapp_http_requests_total{route=\"/javainfo\",status=\"200\"}");
    }

    @Test
    void metricsDoNotCountTheScrapeItself() {
        rest.getForEntity("/metrics", String.class);

        ResponseEntity<String> response = rest.getForEntity("/metrics", String.class);

        assertThat(response.getBody()).doesNotContain("route=\"/metrics\"");
    }
}
