import express, { type Express, type NextFunction, type Request, type Response } from 'express';

import { mib, row, STYLE } from './html.js';
import { httpRequests, registry } from './metrics.js';
import { renderNodeInfo } from './nodeinfo.js';
import * as readings from './readings.js';

/**
 * Normalise a URI into a low-cardinality label: leading/trailing slashes are
 * collapsed so `/nodeinfo/` and `/nodeinfo` share a series.
 */
function route(path: string): string {
  return `/${path.replace(/^\/+|\/+$/g, '')}`;
}

export function createApp(): Express {
  const app = express();

  // Counts every handled request, including the ones that fall through to 404.
  app.use((request: Request, response: Response, next: NextFunction) => {
    response.on('finish', () => {
      const label = route(request.path);

      // Skip the scrape itself, to avoid the exporter counting its own traffic
      // on each Prometheus poll.
      if (label !== '/metrics') {
        httpRequests.inc({ route: label, status: String(response.statusCode) });
      }
    });

    next();
  });

  app.get('/', (_request, response) => {
    response.type('html').send(renderHome());
  });

  app.get('/nodeinfo', (_request, response) => {
    response.type('html').send(renderNodeInfo());
  });

  app.get('/metrics', async (_request, response) => {
    response.set('Content-Type', registry.contentType);
    response.send(await registry.metrics());
  });

  return app;
}

function renderHome(): string {
  const memory = readings.systemMemory();
  const load = readings.systemLoad();
  const cpus = readings.systemCpuCount();
  const cpu = readings.processCpuSeconds();

  const systemMemoryRow =
    memory === null
      ? row('System memory', 'n/a (not Linux)')
      : row(
          'System memory used',
          `${mib(memory.used)} / ${mib(memory.total)} (${Math.round((memory.used / memory.total) * 100)}%)`,
        );

  const loadRow =
    load === null
      ? ''
      : row(
          'Load average (1/5/15m)',
          `${load.one.toFixed(2)} / ${load.five.toFixed(2)} / ${load.fifteen.toFixed(2)} across ${cpus} CPU${cpus === 1 ? '' : 's'}`,
        );

  return `<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>node-app</title>
    ${STYLE}
</head>
<body>
    <h1>🟩 node-app</h1>
    <p>A small TypeScript website served by <strong>Express</strong>.</p>
    <ul>
        <li><a href="/nodeinfo"><code>/nodeinfo</code></a> &mdash; the full Node runtime report</li>
        <li><a href="/metrics"><code>/metrics</code></a> &mdash; Prometheus metrics exporter</li>
    </ul>
    <h2>Live resource usage</h2>
    <table>
        ${systemMemoryRow}
        ${loadRow}
        ${row('Heap memory', `${mib(readings.heapUsedBytes())} used / ${mib(readings.heapTotalBytes())} total`)}
        ${row('Resident set size', mib(readings.residentSetBytes()))}
        ${row('This process CPU', `${cpu.user.toFixed(3)}s user / ${cpu.system.toFixed(3)}s system`)}
        ${row('Uptime', `${readings.uptimeSeconds().toFixed(1)}s`)}
    </table>
    <p><small>Full time series is on <a href="/metrics"><code>/metrics</code></a>.</small></p>
</body>
</html>
`;
}
