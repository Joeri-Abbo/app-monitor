package com.example.javamonitor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Landing page: links to the other routes plus the same live readings the
 * exporter publishes.
 */
@RestController
public class HomeController {

    private final SystemReadings readings;

    HomeController(SystemReadings readings) {
        this.readings = readings;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        String systemMemoryRow = readings.systemMemory()
                .map(memory -> Html.row("System memory used", "%s / %s (%.0f%%)".formatted(
                        Html.mib(memory.used()), Html.mib(memory.total()), memory.usedPercentage())))
                .orElseGet(() -> Html.row("System memory", "n/a (not Linux)"));

        String loadRow = readings.systemLoad()
                .map(load -> Html.row("Load average (1/5/15m)", "%.2f / %.2f / %.2f across %d CPU%s".formatted(
                        load.one(), load.five(), load.fifteen(),
                        readings.systemCpuCount(), readings.systemCpuCount() == 1 ? "" : "s")))
                .orElse("");

        long max = readings.heapMaxBytes();
        String heapRow = Html.row("Heap memory", "%s used / %s committed / %s max".formatted(
                Html.mib(readings.heapUsedBytes()),
                Html.mib(readings.heapCommittedBytes()),
                max < 0 ? "no limit" : Html.mib(max)));
        String nonHeapRow = Html.row("Non-heap memory", Html.mib(readings.nonHeapUsedBytes()));
        String cpuRow = Html.row("This process CPU", "%.3fs".formatted(readings.processCpuSeconds()));
        String threadsRow = Html.row("Live threads", String.valueOf(readings.threadCount()));
        String uptimeRow = Html.row("Uptime", "%.1fs".formatted(readings.uptimeMillis() / 1000.0));

        return """
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="utf-8">
                    <title>java-app</title>
                    %s
                </head>
                <body>
                    <h1>☕ java-app</h1>
                    <p>A small Java website served by <strong>Spring Boot</strong>.</p>
                    <ul>
                        <li><a href="/javainfo"><code>/javainfo</code></a> &mdash; the full JVM report</li>
                        <li><a href="/metrics"><code>/metrics</code></a> &mdash; Prometheus metrics exporter</li>
                    </ul>
                    <h2>Live resource usage</h2>
                    <table>
                        %s
                        %s
                        %s
                        %s
                        %s
                        %s
                        %s
                    </table>
                    <p><small>Full time series is on <a href="/metrics"><code>/metrics</code></a>.</small></p>
                </body>
                </html>
                """.formatted(Html.STYLE, systemMemoryRow, loadRow, heapRow, nonHeapRow, cpuRow, threadsRow, uptimeRow);
    }
}
