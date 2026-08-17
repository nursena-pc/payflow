import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

import { requiredBaseUrl } from '../lib/runtime.js';
import { syntheticAccountActionEmail } from '../lib/workload.js';

const REQUEST_COUNT = 40;
const baseUrl = requiredBaseUrl();
const unexpectedFailures = new Rate('payflow_unexpected_failures');

export const options = {
    discardResponseBodies: true,
    scenarios: {
        account_action_quota_pressure: {
            executor: 'per-vu-iterations',
            vus: REQUEST_COUNT,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        checks: ['rate==1'],
        payflow_unexpected_failures: ['rate==0'],
    },
};

export default function () {
    const email = syntheticAccountActionEmail(
        exec.scenario.iterationInTest
    );

    const response = http.post(
        `${baseUrl}/api/v1/auth/email-verification/requests`,
        JSON.stringify({ email }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { operation: 'account_action_quota_pressure' },
        }
    );

    const coarseAccepted = response.status === 202;
    unexpectedFailures.add(!coarseAccepted);

    check(response, {
        'quota pressure preserves the coarse accepted contract': () =>
            coarseAccepted,
    });
}
