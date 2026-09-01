import assert from 'node:assert/strict';
import type { Server } from 'node:http';
import { after, before, describe, it } from 'node:test';

import { createApp } from './app.js';

describe('node-app', () => {
  let server: Server;
  let base: string;

  before(async () => {
    server = createApp().listen(0);
    await new Promise((resolve) => server.once('listening', resolve));

    const address = server.address();
    assert.ok(address !== null && typeof address === 'object');
    base = `http://127.0.0.1:${address.port}`;
  });

  after(async () => {
    await new Promise((resolve) => server.close(resolve));
  });

  it('lists the other routes on the homepage', async () => {
    const response = await fetch(`${base}/`);
    const body = await response.text();

    assert.equal(response.status, 200);
    for (const expected of ['/nodeinfo', '/metrics', 'Live resource usage']) {
      assert.ok(body.includes(expected), `expected homepage to mention ${expected}`);
    }
  });

  it('reports the runtime on /nodeinfo', async () => {
    const response = await fetch(`${base}/nodeinfo`);
    const body = await response.text();

    assert.equal(response.status, 200);
    for (const expected of ['Node runtime', 'V8 heap statistics', 'Dependency versions']) {
      assert.ok(body.includes(expected), `expected nodeinfo to have a ${expected} section`);
    }
  });

  it("exposes the app's own metric families", async () => {
    await fetch(`${base}/nodeinfo`);

    const response = await fetch(`${base}/metrics`);
    const body = await response.text();

    assert.equal(response.status, 200);
    assert.ok(body.includes('nodeapp_process_heap_used_bytes'));
    assert.ok(body.includes('nodeapp_system_cpu_count'));
    assert.ok(body.includes('nodeapp_http_requests_total{route="/nodeinfo",status="200"}'));
  });

  it('counts requests that fall through to 404', async () => {
    await fetch(`${base}/nope`);

    const body = await (await fetch(`${base}/metrics`)).text();

    assert.ok(body.includes('nodeapp_http_requests_total{route="/nope",status="404"}'));
  });

  it('does not count the scrape itself', async () => {
    await fetch(`${base}/metrics`);

    const body = await (await fetch(`${base}/metrics`)).text();

    assert.ok(!body.includes('route="/metrics"'));
  });
});
