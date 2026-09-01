import os from 'node:os';
import v8 from 'node:v8';

import { escape, mib, row, STYLE, table } from './html.js';
import * as readings from './readings.js';

/**
 * The Node answer to `phpinfo()`: everything the runtime knows about itself,
 * on one page.
 *
 * Unlike `phpinfo()` this deliberately leaves out environment variables, which
 * routinely hold credentials. Even so, the page exposes plenty about the
 * runtime — keep it off the public internet.
 */
export function renderNodeInfo(): string {
  const sections = [
    '<h2>Node runtime</h2>',
    runtimeTable(),
    '<h2>Operating system</h2>',
    operatingSystemTable(),
    '<h2>Memory</h2>',
    memoryTable(),
    '<h2>V8 heap statistics</h2>',
    heapStatisticsTable(),
    '<h2>V8 heap spaces</h2>',
    heapSpacesTable(),
    '<h2>Dependency versions</h2>',
    versionsTable(),
    '<h2>Command line</h2>',
    commandLineTable(),
  ].join('');

  return `<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>nodeinfo</title>
    ${STYLE}
</head>
<body>
    <h1>🟩 nodeinfo</h1>
    <p><a href="/">&larr; back</a></p>
    ${sections}
</body>
</html>
`;
}

function runtimeTable(): string {
  const cpu = readings.processCpuSeconds();

  return table(
    row('Node version', process.version),
    row('V8 version', process.versions.v8),
    row('Platform', `${process.platform} (${process.arch})`),
    row('PID', process.pid),
    row('Uptime', `${readings.uptimeSeconds().toFixed(1)}s`),
    row('Process CPU time', `${cpu.user.toFixed(3)}s user / ${cpu.system.toFixed(3)}s system`),
    row('Executable', process.execPath),
    row('Working directory', process.cwd()),
  );
}

function operatingSystemTable(): string {
  const load = readings.systemLoad();
  const memory = readings.systemMemory();

  return table(
    row('Name', `${os.type()} ${os.release()}`),
    row('Architecture', os.arch()),
    row('Available processors', readings.systemCpuCount()),
    row('CPU model', os.cpus()[0]?.model ?? 'unknown'),
    row(
      'Load average (1/5/15m)',
      load === null
        ? 'n/a (not Linux)'
        : `${load.one.toFixed(2)} / ${load.five.toFixed(2)} / ${load.fifteen.toFixed(2)}`,
    ),
    row(
      'System memory',
      memory === null ? 'n/a (not Linux)' : `${mib(memory.used)} used / ${mib(memory.total)} total`,
    ),
  );
}

function memoryTable(): string {
  const usage = process.memoryUsage();

  return table(
    row('Heap used', mib(usage.heapUsed)),
    row('Heap total', mib(usage.heapTotal)),
    row('Resident set size', mib(usage.rss)),
    row('External', mib(usage.external)),
    row('ArrayBuffers', mib(usage.arrayBuffers)),
  );
}

function heapStatisticsTable(): string {
  const stats = v8.getHeapStatistics();

  return table(
    row('Heap size limit', mib(stats.heap_size_limit)),
    row('Total heap size', mib(stats.total_heap_size)),
    row('Used heap size', mib(stats.used_heap_size)),
    row('Total available size', mib(stats.total_available_size)),
    row('Malloced memory', mib(stats.malloced_memory)),
    row('Number of native contexts', stats.number_of_native_contexts),
    row('Number of detached contexts', stats.number_of_detached_contexts),
  );
}

function heapSpacesTable(): string {
  const rows = v8
    .getHeapSpaceStatistics()
    .map(
      (space) =>
        `<tr><td>${escape(space.space_name)}</td><td>${mib(space.space_used_size)}</td>` +
        `<td>${mib(space.space_size)}</td><td>${mib(space.space_available_size)}</td></tr>`,
    );

  return table('<tr><th>Space</th><th>Used</th><th>Size</th><th>Available</th></tr>', ...rows);
}

function versionsTable(): string {
  const rows = Object.entries(process.versions)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, version]) => row(name, version));

  return table('<tr><th>Component</th><th>Version</th></tr>', ...rows);
}

function commandLineTable(): string {
  const argv = process.argv.map((argument) => `<tr><td colspan="2"><code>${escape(argument)}</code></td></tr>`);
  const execArgv = process.execArgv.length === 0
    ? [row('Node options', 'none')]
    : process.execArgv.map((argument) => `<tr><td colspan="2"><code>${escape(argument)}</code></td></tr>`);

  return table(...argv, ...execArgv);
}
