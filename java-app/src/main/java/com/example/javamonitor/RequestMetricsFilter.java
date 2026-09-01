package com.example.javamonitor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Counts every handled request as {@code javaapp_http_requests_total{route,status}}.
 */
@Component
public class RequestMetricsFilter extends OncePerRequestFilter {

    private final AppMetrics metrics;

    RequestMetricsFilter(AppMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);

        // Skip the scrape itself, to avoid the exporter counting its own traffic
        // on each Prometheus poll.
        String route = route(request.getRequestURI());
        if (!route.equals("/metrics")) {
            metrics.observeRequest(route, response.getStatus());
        }
    }

    /**
     * Normalise a URI into a low-cardinality label: leading/trailing slashes are
     * collapsed so {@code /javainfo/} and {@code /javainfo} share a series.
     */
    private static String route(String uri) {
        return "/" + uri.replaceAll("^/+|/+$", "");
    }
}
