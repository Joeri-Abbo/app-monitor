package com.example.javamonitor;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Java answer to {@code phpinfo()}: everything the JVM knows about itself,
 * on one page.
 *
 * <p>Unlike {@code phpinfo()} this deliberately leaves out environment
 * variables, which routinely hold credentials. Even so, the page exposes plenty
 * about the runtime — keep it off the public internet.
 */
@RestController
public class JavaInfoController {

    private final SystemReadings readings;

    JavaInfoController(SystemReadings readings) {
        this.readings = readings;
    }

    @GetMapping(value = "/javainfo", produces = MediaType.TEXT_HTML_VALUE)
    public String javaInfo() {
        StringBuilder body = new StringBuilder();

        body.append("<h2>Java runtime</h2>").append(runtimeTable());
        body.append("<h2>Operating system</h2>").append(operatingSystemTable());
        body.append("<h2>Memory</h2>").append(memoryTable());
        body.append("<h2>Memory pools</h2>").append(memoryPoolsTable());
        body.append("<h2>Garbage collectors</h2>").append(garbageCollectorsTable());
        body.append("<h2>Threads &amp; classes</h2>").append(threadsAndClassesTable());
        body.append("<h2>JVM arguments</h2>").append(jvmArgumentsTable());
        body.append("<h2>System properties</h2>").append(systemPropertiesTable());

        return """
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="utf-8">
                    <title>javainfo</title>
                    %s
                </head>
                <body>
                    <h1>☕ javainfo</h1>
                    <p><a href="/">&larr; back</a></p>
                    %s
                </body>
                </html>
                """.formatted(Html.STYLE, body);
    }

    private String runtimeTable() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        return table(
                Html.row("Java version", System.getProperty("java.version")),
                Html.row("Java vendor", System.getProperty("java.vendor")),
                Html.row("Java home", System.getProperty("java.home")),
                Html.row("VM name", runtime.getVmName()),
                Html.row("VM version", runtime.getVmVersion()),
                Html.row("VM vendor", runtime.getVmVendor()),
                Html.row("Spring Boot", SpringBootVersion.getVersion()),
                Html.row("Spring Framework", SpringVersion.getVersion()),
                Html.row("Started at", Instant.ofEpochMilli(runtime.getStartTime()).toString()),
                Html.row("Uptime", Duration.ofMillis(runtime.getUptime()).toString()),
                Html.row("PID", String.valueOf(ProcessHandle.current().pid())));
    }

    private String operatingSystemTable() {
        String load = readings.systemLoad()
                .map(l -> "%.2f / %.2f / %.2f".formatted(l.one(), l.five(), l.fifteen()))
                .orElse("n/a (not Linux)");

        String memory = readings.systemMemory()
                .map(m -> "%s used / %s total".formatted(Html.mib(m.used()), Html.mib(m.total())))
                .orElse("n/a (not Linux)");

        return table(
                Html.row("Name", System.getProperty("os.name")),
                Html.row("Version", System.getProperty("os.version")),
                Html.row("Architecture", System.getProperty("os.arch")),
                Html.row("Available processors", String.valueOf(readings.systemCpuCount())),
                Html.row("Load average (1/5/15m)", load),
                Html.row("System memory", memory),
                Html.row("Process CPU time", "%.3fs".formatted(readings.processCpuSeconds())));
    }

    private String memoryTable() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();

        return table(
                Html.row("Heap used", Html.mib(heap.getUsed())),
                Html.row("Heap committed", Html.mib(heap.getCommitted())),
                Html.row("Heap max", heap.getMax() < 0 ? "no limit" : Html.mib(heap.getMax())),
                Html.row("Non-heap used", Html.mib(nonHeap.getUsed())),
                Html.row("Non-heap committed", Html.mib(nonHeap.getCommitted())),
                Html.row("Runtime max memory", Html.mib(Runtime.getRuntime().maxMemory())));
    }

    private String memoryPoolsTable() {
        StringBuilder rows = new StringBuilder("<tr><th>Pool</th><th>Type</th><th>Used</th><th>Committed</th><th>Max</th></tr>");

        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage usage = pool.getUsage();
            if (usage == null) {
                continue;
            }

            rows.append("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>".formatted(
                    Html.escape(pool.getName()),
                    Html.escape(pool.getType().name()),
                    Html.mib(usage.getUsed()),
                    Html.mib(usage.getCommitted()),
                    usage.getMax() < 0 ? "no limit" : Html.mib(usage.getMax())));
        }

        return table(rows.toString());
    }

    private String garbageCollectorsTable() {
        StringBuilder rows = new StringBuilder("<tr><th>Collector</th><th>Collections</th><th>Time spent</th><th>Pools</th></tr>");

        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            rows.append("<tr><td>%s</td><td>%d</td><td>%s</td><td>%s</td></tr>".formatted(
                    Html.escape(gc.getName()),
                    gc.getCollectionCount(),
                    "%.3fs".formatted(gc.getCollectionTime() / 1000.0),
                    Html.escape(String.join(", ", gc.getMemoryPoolNames()))));
        }

        return table(rows.toString());
    }

    private String threadsAndClassesTable() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();

        return table(
                Html.row("Live threads", String.valueOf(threads.getThreadCount())),
                Html.row("Daemon threads", String.valueOf(threads.getDaemonThreadCount())),
                Html.row("Peak threads", String.valueOf(threads.getPeakThreadCount())),
                Html.row("Threads started", String.valueOf(threads.getTotalStartedThreadCount())),
                Html.row("Classes loaded", String.valueOf(classes.getLoadedClassCount())),
                Html.row("Classes loaded (total)", String.valueOf(classes.getTotalLoadedClassCount())),
                Html.row("Classes unloaded", String.valueOf(classes.getUnloadedClassCount())));
    }

    private String jvmArgumentsTable() {
        var arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        if (arguments.isEmpty()) {
            return "<p><small>No JVM arguments were passed.</small></p>";
        }

        StringBuilder rows = new StringBuilder();
        for (String argument : arguments) {
            rows.append("<tr><td colspan=\"2\"><code>%s</code></td></tr>".formatted(Html.escape(argument)));
        }

        return table(rows.toString());
    }

    private String systemPropertiesTable() {
        StringBuilder rows = new StringBuilder("<tr><th>Property</th><th>Value</th></tr>");

        // Sorted so the page diffs cleanly between reloads and between JVMs.
        for (Map.Entry<String, String> property : new TreeMap<>(propertiesAsMap()).entrySet()) {
            rows.append(Html.row(property.getKey(), property.getValue()));
        }

        return table(rows.toString());
    }

    private static Map<String, String> propertiesAsMap() {
        Map<String, String> properties = new TreeMap<>();
        System.getProperties().forEach((key, value) -> properties.put(String.valueOf(key), String.valueOf(value)));

        return properties;
    }

    private static String table(String... rows) {
        return "<table>" + String.join("", rows) + "</table>";
    }
}
