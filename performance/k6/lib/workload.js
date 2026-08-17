const DEFAULT_RATE = 10;
const DEFAULT_DURATION = '120s';
const DEFAULT_PRE_ALLOCATED_VUS = 20;
const DEFAULT_MAX_VUS = 100;

function positiveInteger(name, fallback) {
    const raw = (__ENV[name] || `${fallback}`).trim();
    const value = Number.parseInt(raw, 10);

    if (!Number.isInteger(value) || value <= 0 || `${value}` !== raw) {
        throw new Error(`${name} must be a positive integer.`);
    }

    return value;
}

function durationSeconds(value) {
    const match = /^(\d+)(s|m)$/.exec(value);

    if (!match) {
        throw new Error('K6_DURATION must use whole seconds or minutes.');
    }

    const amount = Number.parseInt(match[1], 10);
    return match[2] === 'm' ? amount * 60 : amount;
}

export function workloadProfile() {
    const value = (__ENV.K6_PROFILE || 'smoke').trim();

    if (value !== 'smoke' && value !== 'steady') {
        throw new Error('K6_PROFILE must be smoke or steady.');
    }

    return value;
}

export function workloadOptions(operation) {
    const profile = workloadProfile();
    const thresholds = {
        checks: ['rate==1'],
        payflow_unexpected_failures: ['rate==0'],
    };

    if (profile === 'smoke') {
        return {
            discardResponseBodies: true,
            scenarios: {
                [operation]: {
                    executor: 'shared-iterations',
                    vus: 1,
                    iterations: 1,
                    maxDuration: '30s',
                },
            },
            thresholds,
        };
    }

    const rate = positiveInteger('K6_RATE', DEFAULT_RATE);
    const duration = (__ENV.PAYFLOW_K6_DURATION || DEFAULT_DURATION).trim();
    durationSeconds(duration);

    thresholds[`http_req_duration{operation:${operation}}`] = [
        'p(95)<=750',
        'p(99)<=1500',
    ];
    thresholds.dropped_iterations = ['count==0'];

    return {
        discardResponseBodies: true,
        scenarios: {
            [operation]: {
                executor: 'constant-arrival-rate',
                rate,
                timeUnit: '1s',
                duration,
                preAllocatedVUs: positiveInteger(
                    'K6_PRE_ALLOCATED_VUS',
                    DEFAULT_PRE_ALLOCATED_VUS
                ),
                maxVUs: positiveInteger('K6_MAX_VUS', DEFAULT_MAX_VUS),
            },
        },
        thresholds,
    };
}

export function requiredIterationCount() {
    if (workloadProfile() === 'smoke') {
        return 1;
    }

    const rate = positiveInteger('K6_RATE', DEFAULT_RATE);
    const duration = (__ENV.PAYFLOW_K6_DURATION || DEFAULT_DURATION).trim();
    return rate * durationSeconds(duration);
}

export function syntheticAccountActionEmail(iteration) {
    const prefix = (__ENV.K6_ACCOUNT_ACTION_EMAIL_PREFIX || 'payflow-load')
        .trim()
        .toLowerCase();

    if (!/^[a-z0-9-]{1,40}$/.test(prefix)) {
        throw new Error(
            'K6_ACCOUNT_ACTION_EMAIL_PREFIX must use only lowercase letters, digits, or hyphens.'
        );
    }

    return `${prefix}-${iteration}@example.invalid`;
}
