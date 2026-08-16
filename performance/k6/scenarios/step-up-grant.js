import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

import { requiredBaseUrl } from '../lib/runtime.js';
import {
    assertFixtureCapacity,
    fixtureForIteration,
    fixtureSection,
    loadFixtureDocument,
} from '../lib/fixtures.js';
import {
    requiredIterationCount,
    workloadOptions,
} from '../lib/workload.js';

const baseUrl = requiredBaseUrl();
const fixtureDocument = loadFixtureDocument();
const fixtures = fixtureSection(fixtureDocument, 'stepUpGrant');
const unexpectedFailures = new Rate('payflow_unexpected_failures');

export const options = workloadOptions('step_up_grant');

export function setup() {
    assertFixtureCapacity(fixtures, requiredIterationCount());
}

export default function () {
    const fixture = fixtureForIteration(
        fixtures,
        exec.scenario.iterationInTest,
        ['accessToken', 'purpose', 'code']
    );

    const response = http.post(
        `${baseUrl}/api/v1/users/me/step-up/grants`,
        JSON.stringify({
            purpose: fixture.purpose,
            code: fixture.code,
        }),
        {
            headers: {
                Authorization: `Bearer ${fixture.accessToken}`,
                'Content-Type': 'application/json',
            },
            tags: { operation: 'step_up_grant' },
        }
    );

    const issued = response.status === 200;
    unexpectedFailures.add(!issued);

    check(response, {
        'step-up fixture issues one purpose-bound grant': () => issued,
    });
}
