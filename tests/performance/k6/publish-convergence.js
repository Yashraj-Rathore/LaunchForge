import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const databaseVisibility = new Trend('launchforge_publish_database_visibility', true);
const redisMaterialization = new Trend('launchforge_publish_redis_materialization', true);
const edgeVisibility = new Trend('launchforge_publish_edge_visibility', true);
const controlBaseUrl = __ENV.CONTROL_BASE_URL || 'http://host.docker.internal:8080';
const edgeBaseUrl = __ENV.EDGE_BASE_URL || 'http://host.docker.internal:8082';
const environmentId = __ENV.ENVIRONMENT_ID || '';
const expectedRevision = Number(__ENV.EXPECTED_REVISION || 0);
const committedAtMillis = Number(__ENV.PUBLISH_COMMITTED_AT_MS || 0);
const sessionCookie = __ENV.SESSION_COOKIE || '';
const sessionCookieName = __ENV.SESSION_COOKIE_NAME || '__Host-launchforge_session';
const sdkKey = __ENV.SDK_KEY || '';
let databaseRecorded = false;
let redisRecorded = false;
let edgeRecorded = false;

export const options = {
  scenarios: {
    convergence_probe: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: Number(__ENV.MAX_POLLS || 60),
      maxDuration: __ENV.MAX_DURATION || '2m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
  },
};

export function setup() {
  if (!environmentId || expectedRevision < 1 || committedAtMillis < 1 || !sessionCookie || !sdkKey) {
    throw new Error(
      'ENVIRONMENT_ID, EXPECTED_REVISION, PUBLISH_COMMITTED_AT_MS, SESSION_COOKIE, and SDK_KEY are required',
    );
  }
}

export default function () {
  const elapsed = Date.now() - committedAtMillis;
  const diagnostic = http.get(
    controlBaseUrl + '/api/v1/environments/' + environmentId + '/diagnostics/revision',
    {
      headers: {
        Cookie: sessionCookieName + '=' + sessionCookie,
        Accept: 'application/json',
      },
      tags: { route: 'revision_diagnostics' },
      timeout: __ENV.REQUEST_TIMEOUT || '5s',
    },
  );
  const snapshot = http.get(edgeBaseUrl + '/sdk/v1/snapshot', {
    headers: {
      Authorization: 'LF-SDK ' + sdkKey,
      Accept: 'application/json',
    },
    tags: { route: 'snapshot_convergence' },
    timeout: __ENV.REQUEST_TIMEOUT || '5s',
  });
  check(diagnostic, {
    'diagnostic request succeeds': (value) => value.status === 200,
    'database revision is monotonic': (value) =>
      value.status === 200 && value.json('databaseRevision') >= expectedRevision,
  });
  check(snapshot, {
    'edge snapshot succeeds': (value) => value.status === 200,
    'edge revision does not exceed database': (value) =>
      value.status === 200 &&
      Number(value.headers['X-Launchforge-Revision'] || value.headers['X-LaunchForge-Revision']) <=
        diagnostic.json('databaseRevision'),
  });
  if (!databaseRecorded && diagnostic.status === 200 && diagnostic.json('databaseRevision') >= expectedRevision) {
    databaseVisibility.add(elapsed);
    databaseRecorded = true;
  }
  if (!redisRecorded && diagnostic.status === 200 && diagnostic.json('redisRevision') >= expectedRevision) {
    redisMaterialization.add(elapsed);
    redisRecorded = true;
  }
  const edgeRevision = Number(
    snapshot.headers['X-Launchforge-Revision'] || snapshot.headers['X-LaunchForge-Revision'] || 0,
  );
  if (!edgeRecorded && snapshot.status === 200 && edgeRevision >= expectedRevision) {
    edgeVisibility.add(elapsed);
    edgeRecorded = true;
  }
  if (!(databaseRecorded && redisRecorded && edgeRecorded)) {
    sleep(Number(__ENV.POLL_INTERVAL_SECONDS || 1));
  }
}
