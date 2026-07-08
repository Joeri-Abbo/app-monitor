<?php

declare(strict_types=1);

namespace App;

use Prometheus\CollectorRegistry;
use Prometheus\Exception\StorageException;
use Prometheus\RenderTextFormat;
use Prometheus\Storage\APC;

/**
 * Thin wrapper around the Prometheus client plus a few live host/process
 * readings.
 *
 * APCu is used as the storage backend so counters survive between requests
 * (every PHP request is a fresh process, so an in-memory registry would reset
 * to zero on every scrape). The registry is created lazily so that pages which
 * do not need metrics still render if the APCu extension is unavailable.
 */
final class Metrics
{
    private ?CollectorRegistry $registry = null;

    private function registry(): CollectorRegistry
    {
        return $this->registry ??= new CollectorRegistry(new APC());
    }

    /**
     * Record a single handled HTTP request. Never fatal: if the metrics backend
     * is unavailable the request itself should still succeed.
     */
    public function observeRequest(string $route, int $status): void
    {
        try {
            $this->registry()
                ->getOrRegisterCounter(
                    'phpapp',
                    'http_requests_total',
                    'Total number of HTTP requests handled.',
                    ['route', 'status'],
                )
                ->inc([$route, (string) $status]);
        } catch (StorageException) {
            // APCu not loaded — skip recording rather than break the response.
        }
    }

    /**
     * Render the current metric families in Prometheus text exposition format.
     */
    public function render(): string
    {
        $registry = $this->registry();

        $gauge = static function (string $name, string $help, float $value) use ($registry): void {
            $registry->getOrRegisterGauge('phpapp', $name, $help)->set($value);
        };

        // --- Per-process (this PHP worker, refreshed each scrape) ------------
        $gauge('process_memory_bytes', 'Memory used by the current PHP process in bytes.', (float) $this->processMemoryBytes());
        $gauge('process_memory_peak_bytes', 'Peak memory used by the current PHP process in bytes.', (float) $this->processMemoryPeakBytes());

        $cpu = $this->processCpu();
        $gauge('process_cpu_user_seconds', 'User CPU time consumed by the current PHP process.', $cpu['user']);
        $gauge('process_cpu_system_seconds', 'System CPU time consumed by the current PHP process.', $cpu['system']);

        // --- Container/host wide ---------------------------------------------
        if (($mem = $this->systemMemory()) !== null) {
            $gauge('system_memory_total_bytes', 'Total system memory in bytes.', (float) $mem['total']);
            $gauge('system_memory_used_bytes', 'Used system memory in bytes (total - available).', (float) $mem['used']);
            $gauge('system_memory_available_bytes', 'Available system memory in bytes.', (float) $mem['available']);
        }

        if (($cpus = $this->systemCpuCount()) !== null) {
            $gauge('system_cpu_count', 'Number of logical CPUs visible to the container.', (float) $cpus);
        }

        if (($load = $this->systemLoad()) !== null) {
            $gauge('system_load1', 'System load average over the last 1 minute.', $load[0]);
            $gauge('system_load5', 'System load average over the last 5 minutes.', $load[1]);
            $gauge('system_load15', 'System load average over the last 15 minutes.', $load[2]);
        }

        return (new RenderTextFormat())->render($registry->getMetricFamilySamples());
    }

    public function contentType(): string
    {
        return RenderTextFormat::MIME_TYPE;
    }

    // -- Raw readings (also reused by the homepage) ---------------------------

    public function processMemoryBytes(): int
    {
        return memory_get_usage(true);
    }

    public function processMemoryPeakBytes(): int
    {
        return memory_get_peak_usage(true);
    }

    /**
     * @return array{user: float, system: float} CPU seconds for this process.
     */
    public function processCpu(): array
    {
        $u = function_exists('getrusage') ? getrusage() : [];

        $seconds = static fn (string $sec, string $usec): float =>
            (float) ($u[$sec] ?? 0) + (float) ($u[$usec] ?? 0) / 1_000_000;

        return [
            'user' => $seconds('ru_utime.tv_sec', 'ru_utime.tv_usec'),
            'system' => $seconds('ru_stime.tv_sec', 'ru_stime.tv_usec'),
        ];
    }

    /**
     * System memory from /proc/meminfo (Linux only).
     *
     * @return array{total: int, used: int, available: int}|null
     */
    public function systemMemory(): ?array
    {
        if (!is_readable('/proc/meminfo')) {
            return null;
        }

        $info = (string) file_get_contents('/proc/meminfo');
        $read = static function (string $key) use ($info): ?int {
            if (preg_match('/^' . preg_quote($key, '/') . ':\s+(\d+)\s+kB/m', $info, $m) === 1) {
                return (int) $m[1] * 1024;
            }

            return null;
        };

        $total = $read('MemTotal');
        $available = $read('MemAvailable');
        if ($total === null || $available === null) {
            return null;
        }

        return [
            'total' => $total,
            'available' => $available,
            'used' => $total - $available,
        ];
    }

    public function systemCpuCount(): ?int
    {
        if (!is_readable('/proc/cpuinfo')) {
            return null;
        }

        $count = preg_match_all('/^processor\s*:/m', (string) file_get_contents('/proc/cpuinfo'));

        return $count > 0 ? $count : null;
    }

    /**
     * @return array{0: float, 1: float, 2: float}|null Load averages (1/5/15m).
     */
    public function systemLoad(): ?array
    {
        if (!function_exists('sys_getloadavg')) {
            return null;
        }

        $load = sys_getloadavg();

        return $load === false ? null : [$load[0], $load[1], $load[2]];
    }
}
