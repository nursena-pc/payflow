import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

import { requiredBaseUrl } from '../lib/runtime.js';
import {
    registrationExperimentOptions,
    requiredRegistrationPassword,
    syntheticRegistrationEmail,
} from '../lib/workload.js';

const baseUrl = requiredBaseUrl();
const registrationRequests = new Counter(
    'payflow_registration_requests'
);
const registrationCreated = new Counter(
    'payflow_registration_created'
);
const registrationDuration = new Trend(
    'payflow_registration_request_duration',
    true
);
const unexpectedFailures = new Rate(
    'payflow_registration_unexpected_failures'
);
const healthProbeRequests = new Counter(
    'payflow_registration_health_probe_requests'
);
const healthProbeFailures = new Rate(
    'payflow_registration_health_probe_failures'
);

export const options = registrationExperimentOptions();

export function registration() {
    const email = syntheticRegistrationEmail(
        exec.scenario.iterationInTest
    );
    const password = requiredRegistrationPassword();

    const response = http.post(
        `${baseUrl}/api/v1/auth/register`,
        JSON.stringify({ email, password }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
            tags: {
                operation: 'registration_experiment',
            },
        }
    );

    registrationRequests.add(1);
    registrationDuration.add(response.timings.duration);

    const created = response.status === 201;

    registrationCreated.add(created ? 1 : 0);
    unexpectedFailures.add(!created);

    check(response, {
        'registration keeps the existing 201 contract': () => created,
    });
}

export function healthProbe() {
    const response = http.get(
        `${baseUrl}/api/v1/system/health`,
        {
            tags: {
                operation: 'registration_health_probe',
            },
        }
    );

    healthProbeRequests.add(1);
    healthProbeFailures.add(response.status !== 200);
}
