import { createHash } from 'node:crypto';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

const CLIENT_KEY = 'lf_client_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';

export default defineConfig({
  plugins: [react(), ...(process.env.LAUNCHFORGE_DEMO_TEST === 'true' ? [demoEdgeHarness()] : [])],
});

function demoEdgeHarness() {
  let revision = 1;
  let enabled = true;
  const streams = new Set();
  return {
    name: 'launchforge-demo-edge-harness',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const url = new URL(request.url ?? '/', 'http://127.0.0.1:5174');
        const base = `/sdk/v1/client/${CLIENT_KEY}`;
        if (request.method === 'GET' && url.pathname === `${base}/snapshot`) {
          const body = snapshot(revision, enabled);
          const etag = `"demo-rev-${revision}-${body.checksum}"`;
          response.setHeader('Cache-Control', 'no-store');
          response.setHeader('ETag', etag);
          response.setHeader('X-LaunchForge-Revision', String(revision));
          response.setHeader('X-LaunchForge-Checksum', body.checksum);
          response.setHeader('X-LaunchForge-Schema-Version', '1');
          if (request.headers['if-none-match'] === etag) {
            response.statusCode = 304;
            response.end();
          } else {
            response.setHeader('Content-Type', 'application/json');
            response.end(body.json);
          }
          return;
        }
        if (request.method === 'GET' && url.pathname === `${base}/stream`) {
          response.statusCode = 200;
          response.setHeader('Content-Type', 'text/event-stream');
          response.setHeader('Cache-Control', 'no-store');
          response.setHeader('Connection', 'keep-alive');
          response.flushHeaders();
          response.write(': launchforge-heartbeat\n\n');
          streams.add(response);
          request.on('close', () => streams.delete(response));
          return;
        }
        if (request.method === 'POST' && url.pathname === '/demo/publish-kill-switch') {
          revision = 2;
          enabled = false;
          for (const stream of streams) {
            stream.write('id: 2\nevent: revision\ndata: {"revision":2}\n\n');
          }
          response.statusCode = 204;
          response.end();
          return;
        }
        next();
      });
    },
  };
}

function snapshot(revision, enabled) {
  const projection = {
    schemaVersion: 1,
    algorithmVersion: 1,
    projectKey: 'storefront',
    environmentKey: 'development',
    revision,
    generatedAt: '2026-08-12T12:00:00Z',
    flags: {
      'new-checkout': {
        type: 'boolean',
        enabled,
        clientVisible: true,
        variations: [
          { id: 'off', value: false },
          { id: 'on', value: true },
        ],
        offVariation: 'off',
        defaultVariation: 'off',
        rules: [
          {
            id: 'canada-pro',
            conditions: [
              {
                attribute: 'country',
                attributeType: 'string',
                operator: 'EQUALS',
                values: ['CA'],
              },
              {
                attribute: 'plan',
                attributeType: 'string',
                operator: 'EQUALS',
                values: ['pro'],
              },
            ],
            variation: 'on',
          },
        ],
        rollout: {
          attribute: 'userId',
          salt: 'stable_salt_1234',
          weights: [
            { variation: 'on', weight: 50000 },
            { variation: 'off', weight: 50000 },
          ],
        },
      },
    },
  };
  const checksum = createHash('sha256').update(canonicalize(projection), 'utf8').digest('hex');
  return { checksum, json: canonicalize({ ...projection, checksum }) };
}

function canonicalize(value) {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map(canonicalize).join(',')}]`;
  }
  return `{${Object.keys(value)
    .sort()
    .map((key) => `${JSON.stringify(key)}:${canonicalize(value[key])}`)
    .join(',')}}`;
}
