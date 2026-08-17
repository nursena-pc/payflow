import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

import { requiredBaseUrl } from '../lib/runtime.js';
import {
    syntheticAccountActionEmail,
    workloadOptions,
} from '../lib/workload.js';

const baseUrl = requiredBaseUrl();
const unexpectedFailures = new Rate('payflow_unexpected_failures');

export const options = workloadOptions('account_action_request');

export default function () {
    const email = syntheticAccountActionEmail(
        exec.scenario.iterationInTest
    );

    const response = http.post(
        `${baseUrl}/api/v1/auth/email-verification/requests`,
        JSON.stringify({ email }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { operation: 'account_action_request' },
        }
    );

    const accepted = response.status === 202;
    unexpectedFailures.add(!accepted);

    check(response, {
        'account-action request keeps the coarse accepted contract': () =>
            accepted,
    });
}
