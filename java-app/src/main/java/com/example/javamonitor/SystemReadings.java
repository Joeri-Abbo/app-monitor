package com.example.javamonitor;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Live JVM and host readings, shared by the homepage, the /javainfo report and
 * the Prometheus gauges.
 *
 * <p>The JVM numbers come from the standard management beans. The host-wide
 * numbers are read straight from {@code /proc}, exactly like the PHP app does,
 * so both containers report memory and load the same way — the JVM's own
 * {@code OperatingSystemMXBean} only exposes a 1-minute load average.
 */
@Component
public class SystemReadings {

    private static final Pattern MEMINFO_KB = Pattern.compile("^(\\w+):\\s+(\\d+)\\s+kB$", Pattern.MULTILINE);

    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

    /** Host memory in bytes; {@code used} is {@code total - available}. */
    public record SystemMemory(long total, long available, long used) {

        public double usedPercentage() {
            return total == 0 ? 0 : (double) used / total * 100;
        }
    }

    /** Load averages over the last 1, 5 and 15 minutes. */
    public record LoadAverage(double one, double five, double fifteen) {
    }

    // -- JVM ------------------------------------------------------------------

    public long heapUsedBytes() {
        return memory.getHeapMemoryUsage().getUsed();
    }

    public long heapCommittedBytes() {
        return memory.getHeapMemoryUsage().getCommitted();
    }

    /** {@code -1} when the JVM has no heap ceiling configured. */
    public long heapMaxBytes() {
        return memory.getHeapMemoryUsage().getMax();
    }

    public long nonHeapUsedBytes() {
        return memory.getNonHeapMemoryUsage().getUsed();
    }

    public int threadCount() {
        return threads.getThreadCount();
    }

    public long uptimeMillis() {
        return runtime.getUptime();
    }

    /**
     * CPU seconds consumed by this JVM. Falls back to {@code 0} on JVMs that do
     * not expose the HotSpot extension of {@link OperatingSystemMXBean}.
     */
    public double processCpuSeconds() {
        if (os instanceof com.sun.management.OperatingSystemMXBean hotspot) {
            long nanos = hotspot.getProcessCpuTime();
            if (nanos >= 0) {
                return nanos / 1_000_000_000.0;
            }
        }

        return 0;
    }

    // -- Host -----------------------------------------------------------------

    public int systemCpuCount() {
        return os.getAvailableProcessors();
    }

    /** Host memory from {@code /proc/meminfo} (Linux only). */
    public Optional<SystemMemory> systemMemory() {
        return readProc("/proc/meminfo").flatMap(info -> {
            Long total = null;
            Long available = null;

            Matcher matcher = MEMINFO_KB.matcher(info);
            while (matcher.find()) {
                long bytes = Long.parseLong(matcher.group(2)) * 1024;
                switch (matcher.group(1)) {
                    case "MemTotal" -> total = bytes;
                    case "MemAvailable" -> available = bytes;
                    default -> {
                        // Not interested in the rest of /proc/meminfo.
                    }
                }
            }

            if (total == null || available == null) {
                return Optional.empty();
            }

            return Optional.of(new SystemMemory(total, available, total - available));
        });
    }

    /** Load averages from {@code /proc/loadavg} (Linux only). */
    public Optional<LoadAverage> systemLoad() {
        return readProc("/proc/loadavg").flatMap(contents -> {
            String[] fields = contents.trim().split("\\s+");
            if (fields.length < 3) {
                return Optional.empty();
            }

            try {
                return Optional.of(new LoadAverage(
                        Double.parseDouble(fields[0]),
                        Double.parseDouble(fields[1]),
                        Double.parseDouble(fields[2])));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static Optional<String> readProc(String path) {
        Path file = Path.of(path);
        if (!Files.isReadable(file)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Files.readString(file));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
