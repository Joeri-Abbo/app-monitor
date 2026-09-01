package com.example.javamonitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

import java.util.function.ToDoubleFunction;

import org.springframework.stereotype.Component;

/**
 * Registers the app's own {@code javaapp_*} metric family on top of the JVM
 * metrics Spring Boot Actuator already binds ({@code jvm_*}, {@code process_*}).
 *
 * <p>Gauges are registered once with a supplier, so Micrometer re-reads them on
 * every scrape; only the request counter is written from request handling.
 */
@Component
public class AppMetrics {

    private final MeterRegistry registry;
    private final SystemReadings readings;

    AppMetrics(MeterRegistry registry, SystemReadings readings) {
        this.registry = registry;
        this.readings = readings;
    }

    /** Record a single handled HTTP request. */
    public void observeRequest(String route, int status) {
        Counter.builder("javaapp.http.requests")
                .description("Total number of HTTP requests handled.")
                .tag("route", route)
                .tag("status", String.valueOf(status))
                .register(registry)
                .increment();
    }

    @PostConstruct
    void registerGauges() {
        // --- This JVM ---------------------------------------------------------
        gauge("javaapp.process.heap.used.bytes", "Heap memory currently used by the JVM.",
                SystemReadings::heapUsedBytes);
        gauge("javaapp.process.heap.committed.bytes", "Heap memory committed to the JVM by the OS.",
                SystemReadings::heapCommittedBytes);
        gauge("javaapp.process.heap.max.bytes", "Maximum heap size the JVM will grow to (-1 if unbounded).",
                SystemReadings::heapMaxBytes);
        gauge("javaapp.process.nonheap.used.bytes", "Non-heap memory (metaspace, code cache) used by the JVM.",
                SystemReadings::nonHeapUsedBytes);
        gauge("javaapp.process.cpu.seconds", "CPU time consumed by the JVM process.",
                SystemReadings::processCpuSeconds);
        gauge("javaapp.process.threads", "Live threads in the JVM.",
                SystemReadings::threadCount);
        gauge("javaapp.process.uptime.seconds", "Time since the JVM started.",
                r -> r.uptimeMillis() / 1000.0);

        // --- Container/host wide ---------------------------------------------
        gauge("javaapp.system.cpu.count", "Number of logical CPUs visible to the container.",
                SystemReadings::systemCpuCount);

        // Registered only when /proc is readable, so non-Linux hosts export
        // nothing rather than a series of NaNs.
        if (readings.systemMemory().isPresent()) {
            gauge("javaapp.system.memory.total.bytes", "Total system memory in bytes.",
                    r -> r.systemMemory().map(SystemReadings.SystemMemory::total).orElse(0L));
            gauge("javaapp.system.memory.used.bytes", "Used system memory in bytes (total - available).",
                    r -> r.systemMemory().map(SystemReadings.SystemMemory::used).orElse(0L));
            gauge("javaapp.system.memory.available.bytes", "Available system memory in bytes.",
                    r -> r.systemMemory().map(SystemReadings.SystemMemory::available).orElse(0L));
        }

        if (readings.systemLoad().isPresent()) {
            gauge("javaapp.system.load1", "System load average over the last 1 minute.",
                    r -> r.systemLoad().map(SystemReadings.LoadAverage::one).orElse(0.0));
            gauge("javaapp.system.load5", "System load average over the last 5 minutes.",
                    r -> r.systemLoad().map(SystemReadings.LoadAverage::five).orElse(0.0));
            gauge("javaapp.system.load15", "System load average over the last 15 minutes.",
                    r -> r.systemLoad().map(SystemReadings.LoadAverage::fifteen).orElse(0.0));
        }
    }

    private void gauge(String name, String description, ToDoubleFunction<SystemReadings> value) {
        Gauge.builder(name, readings, value)
                .description(description)
                .strongReference(true)
                .register(registry);
    }
}
