import { collectDefaultMetrics, Counter, Gauge, Registry } from 'prom-client';

import * as readings from './readings.js';

/**
 * Registers the app's own `nodeapp_*` metric family on top of prom-client's
 * default metrics (`nodejs_*`, `process_*`), which already cover V8 heap
 * spaces, event-loop lag, handles and GC.
 *
 * Gauges are registered once with a `collect` callback, so prom-client re-reads
 * them on every scrape; only the request counter is written from request
 * handling.
 */
export const registry = new Registry();

collectDefaultMetrics({ register: registry });

export const httpRequests = new Counter({
  name: 'nodeapp_http_requests_total',
  help: 'Total number of HTTP requests handled.',
  labelNames: ['route', 'status'] as const,
  registers: [registry],
});

function gauge(name: string, help: string, value: () => number): void {
  new Gauge({
    name,
    help,
    registers: [registry],
    collect(this: Gauge) {
      this.set(value());
    },
  });
}

// --- This process ----------------------------------------------------------
gauge('nodeapp_process_heap_used_bytes', 'V8 heap currently used by the process.', readings.heapUsedBytes);
gauge('nodeapp_process_heap_total_bytes', 'V8 heap allocated to the process.', readings.heapTotalBytes);
gauge('nodeapp_process_rss_bytes', 'Resident set size of the process.', readings.residentSetBytes);
gauge('nodeapp_process_external_bytes', 'Memory used by C++ objects bound to JavaScript.', readings.externalBytes);
gauge('nodeapp_process_cpu_user_seconds', 'User CPU time consumed by the process.', () => readings.processCpuSeconds().user);
gauge('nodeapp_process_cpu_system_seconds', 'System CPU time consumed by the process.', () => readings.processCpuSeconds().system);
gauge('nodeapp_process_uptime_seconds', 'Time since the process started.', readings.uptimeSeconds);

// --- Container/host wide ---------------------------------------------------
gauge('nodeapp_system_cpu_count', 'Number of logical CPUs visible to the container.', readings.systemCpuCount);

// Registered only when /proc is readable, so non-Linux hosts export nothing
// rather than a series of zeroes.
if (readings.systemMemory() !== null) {
  gauge('nodeapp_system_memory_total_bytes', 'Total system memory in bytes.', () => readings.systemMemory()?.total ?? 0);
  gauge('nodeapp_system_memory_used_bytes', 'Used system memory in bytes (total - available).', () => readings.systemMemory()?.used ?? 0);
  gauge('nodeapp_system_memory_available_bytes', 'Available system memory in bytes.', () => readings.systemMemory()?.available ?? 0);
}

if (readings.systemLoad() !== null) {
  gauge('nodeapp_system_load1', 'System load average over the last 1 minute.', () => readings.systemLoad()?.one ?? 0);
  gauge('nodeapp_system_load5', 'System load average over the last 5 minutes.', () => readings.systemLoad()?.five ?? 0);
  gauge('nodeapp_system_load15', 'System load average over the last 15 minutes.', () => readings.systemLoad()?.fifteen ?? 0);
}
