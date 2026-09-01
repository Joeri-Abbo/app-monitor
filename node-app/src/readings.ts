import { readFileSync } from 'node:fs';
import os from 'node:os';

/**
 * Live process and host readings, shared by the homepage, the /nodeinfo report
 * and the Prometheus gauges.
 *
 * The host-wide numbers are read straight from `/proc`, exactly like the PHP,
 * Java and .NET apps do, so every container reports memory and load the same
 * way. On non-Linux hosts they simply return `null` and the callers omit them.
 */

export interface SystemMemory {
  total: number;
  available: number;
  used: number;
}

export interface LoadAverage {
  one: number;
  five: number;
  fifteen: number;
}

export interface CpuSeconds {
  user: number;
  system: number;
}

export function heapUsedBytes(): number {
  return process.memoryUsage().heapUsed;
}

export function heapTotalBytes(): number {
  return process.memoryUsage().heapTotal;
}

export function residentSetBytes(): number {
  return process.memoryUsage().rss;
}

/** Memory held by C++ objects bound to JavaScript (buffers, sockets, …). */
export function externalBytes(): number {
  return process.memoryUsage().external;
}

export function processCpuSeconds(): CpuSeconds {
  const usage = process.cpuUsage();

  return { user: usage.user / 1_000_000, system: usage.system / 1_000_000 };
}

export function uptimeSeconds(): number {
  return process.uptime();
}

export function systemCpuCount(): number {
  return os.cpus().length;
}

/** Host memory from `/proc/meminfo` (Linux only). */
export function systemMemory(): SystemMemory | null {
  const info = readProc('/proc/meminfo');
  if (info === null) {
    return null;
  }

  const read = (key: string): number | null => {
    const match = new RegExp(`^${key}:\\s+(\\d+)\\s+kB$`, 'm').exec(info);

    return match?.[1] === undefined ? null : Number(match[1]) * 1024;
  };

  const total = read('MemTotal');
  const available = read('MemAvailable');
  if (total === null || available === null) {
    return null;
  }

  return { total, available, used: total - available };
}

/** Load averages from `/proc/loadavg` (Linux only). */
export function systemLoad(): LoadAverage | null {
  const contents = readProc('/proc/loadavg');
  if (contents === null) {
    return null;
  }

  const fields = contents.trim().split(/\s+/);
  const [one, five, fifteen] = [fields[0], fields[1], fields[2]].map(Number);
  if (one === undefined || five === undefined || fifteen === undefined) {
    return null;
  }

  if ([one, five, fifteen].some(Number.isNaN)) {
    return null;
  }

  return { one, five, fifteen };
}

function readProc(path: string): string | null {
  try {
    return readFileSync(path, 'utf8');
  } catch {
    return null;
  }
}
