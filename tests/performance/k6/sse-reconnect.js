import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const reconnectLatency = new Trend('launchforge_sse_reconnect_latency', true);
const acceptedConnections = new Counter('launchforge_sse_connections_accepted');
const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8082';
const sdkKey = __ENV.SDK_KEY || '';

export const options = {
  scenarios: {
    reconnecting_streams: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
  },
};

export function setup() {
  if (!sdkKey) {
    throw new Error('SDK_KEY is required');
  }
}

export default function () {
  const response = http.get(baseUrl + '/sdk/v1/stream', {
    headers: {
      Authorization: 'LF-SDK ' + sdkKey,
      Accept: 'text/event-stream',
      'Last-Event-ID': __ENV.LAST_EVENT_ID || '0',
    },
    tags: { route: 'stream' },
    timeout: __ENV.STREAM_HOLD || '5s',
  });
  reconnectLatency.add(response.timings.blocked + response.timings.connecting + response.timings.tls_handshaking);
  const accepted = response.status === 200 || response.error_code === 1050;
  if (accepted) {
    acceptedConnections.add(1);
  }
  check(response, {
    'stream accepted or held until timeout': () => accepted,
    'rate limit is explicit': (value) =>
      value.status !== 429 || Number(value.headers['Retry-After']) > 0,
  });
  sleep(Number(__ENV.RECONNECT_DELAY_SECONDS || 0.25));
}
