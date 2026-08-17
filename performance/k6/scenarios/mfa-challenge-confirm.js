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
const fixtures = fixtureSection(fixtureDocument, 'mfaChallengeConfirm');
const unexpectedFailures = new Rate('payflow_unexpected_failures');

export const options = workloadOptions('mfa_challenge_confirm');

export function setup() {
    assertFixtureCapacity(fixtures, requiredIterationCount());
}

export default function () {
    const fixture = fixtureForIteration(
        fixtures,
        exec.scenario.iterationInTest,
        ['challengeToken', 'code']
    );

    const response = http.post(
        `${baseUrl}/api/v1/auth/mfa/challenges/confirm`,
        JSON.stringify({
            challengeToken: fixture.challengeToken,
            code: fixture.code,
        }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { operation: 'mfa_challenge_confirm' },
        }
    );

    const completed = response.status === 200;
    unexpectedFailures.add(!completed);

    check(response, {
        'MFA challenge fixture is consumed successfully': () => completed,
    });
}
