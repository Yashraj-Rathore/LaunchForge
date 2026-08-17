import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const snapshotLatency = new Trend('launchforge_snapshot_latency', true);
const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8082';
const sdkKey = __ENV.SDK_KEY || '';
const conditional = (__ENV.CONDITIONAL || 'true').toLowerCase() === 'true';
const sourceProfile = __ENV.SOURCE_PROFILE || 'redis_warm';
let etag = '';

export const options = {
  scenarios: {
    snapshot_reads: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
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
  if (!['redis_warm', 'postgres_fallback'].includes(sourceProfile)) {
    throw new Error('SOURCE_PROFILE must be redis_warm or postgres_fallback');
  }
}

export default function () {
  const headers = {
    Authorization: 'LF-SDK ' + sdkKey,
    Accept: 'application/json',
  };
  if (conditional && etag) {
    headers['If-None-Match'] = etag;
  }
  const response = http.get(baseUrl + '/sdk/v1/snapshot', {
    headers,
    tags: { route: 'snapshot', source_profile: sourceProfile },
    timeout: __ENV.REQUEST_TIMEOUT || '5s',
  });
  snapshotLatency.add(response.timings.duration);
  check(response, {
    'snapshot is 200 or 304': (value) => value.status === 200 || value.status === 304,
    'revision header is positive': (value) =>
      Number(value.headers['X-Launchforge-Revision'] || value.headers['X-LaunchForge-Revision']) > 0,
    'checksum header is SHA-256': (value) =>
      /^[0-9a-f]{64}$/.test(
        value.headers['X-Launchforge-Checksum'] || value.headers['X-LaunchForge-Checksum'] || '',
      ),
  });
  if (response.status === 200 && response.headers.ETag) {
    etag = response.headers.ETag;
  }
}
