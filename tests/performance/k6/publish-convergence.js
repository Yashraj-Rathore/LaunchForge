import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const convergence = new Trend('launchforge_publish_convergence', true);
const baseUrl = __ENV.CONTROL_BASE_URL || 'http://host.docker.internal:8080';
const environmentId = __ENV.ENVIRONMENT_ID || '';
const expectedRevision = Number(__ENV.EXPECTED_REVISION || 0);
const committedAtMillis = Number(__ENV.PUBLISH_COMMITTED_AT_MS || 0);
const sessionCookie = __ENV.SESSION_COOKIE || '';

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
  if (!environmentId || expectedRevision < 1 || committedAtMillis < 1 || !sessionCookie) {
    throw new Error(
      'ENVIRONMENT_ID, EXPECTED_REVISION, PUBLISH_COMMITTED_AT_MS, and SESSION_COOKIE are required',
    );
  }
}

export default function () {
  const response = http.get(
    baseUrl + '/api/v1/environments/' + environmentId + '/diagnostics/revision',
    {
      headers: {
        Cookie: '__Host-launchforge_session=' + sessionCookie,
        Accept: 'application/json',
      },
      tags: { route: 'revision_diagnostics' },
      timeout: __ENV.REQUEST_TIMEOUT || '5s',
    },
  );
  const valid = check(response, {
    'diagnostic request succeeds': (value) => value.status === 200,
    'database revision is monotonic': (value) =>
      value.status === 200 && value.json('databaseRevision') >= expectedRevision,
  });
  if (valid && response.json('edgeResolvableRevision') >= expectedRevision) {
    convergence.add(Date.now() - committedAtMillis);
    return;
  }
  sleep(Number(__ENV.POLL_INTERVAL_SECONDS || 1));
}
