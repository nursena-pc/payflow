import http from 'k6/http';
import { check } from 'k6';

import { requiredBaseUrl } from '../lib/runtime.js';

const baseUrl = requiredBaseUrl();

export const options = {
    discardResponseBodies: false,
    scenarios: {
        harness_smoke: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        http_req_duration: ['p(100)<5000'],
    },
};

export default function () {
    const response = http.get(`${baseUrl}/api/v1/system/health`, {
        tags: {
            operation: 'system_health',
        },
    });

    check(response, {
        'health response is successful': (result) =>
            result.status >= 200 && result.status < 300,
    });
}
