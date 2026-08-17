import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

import { requiredBaseUrl } from '../lib/runtime.js';
import {
    evidenceWorkloadOptions,
    syntheticAccountActionEmail,
} from '../lib/workload.js';

const baseUrl = requiredBaseUrl();
const evidenceRequests = new Counter('payflow_evidence_requests');
const evidenceDuration = new Trend('payflow_evidence_request_duration', true);
const unexpectedFailures = new Rate('payflow_unexpected_failures');
const healthProbeRequests = new Counter('payflow_health_probe_requests');
const healthProbeFailures = new Rate('payflow_health_probe_failures');

export const options = evidenceWorkloadOptions('account_action_evidence');

export function protectedWorkflow() {
    const email = syntheticAccountActionEmail(
        exec.scenario.iterationInTest
    );

    const response = http.post(
        `${baseUrl}/api/v1/auth/email-verification/requests`,
        JSON.stringify({ email }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { operation: 'account_action_evidence' },
        }
    );

    evidenceRequests.add(1);
    evidenceDuration.add(response.timings.duration);

    const accepted = response.status === 202;
    unexpectedFailures.add(!accepted);

    check(response, {
        'evidence request keeps the coarse accepted contract': () => accepted,
    });
}

export function healthProbe() {
    const response = http.get(
        `${baseUrl}/api/v1/system/health`,
        { tags: { operation: 'health_probe' } }
    );

    healthProbeRequests.add(1);
    healthProbeFailures.add(response.status !== 200);
}
