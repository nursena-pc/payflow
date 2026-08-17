export function requiredBaseUrl() {
    const value = (__ENV.BASE_URL || '').trim();

    if (value.length === 0) {
        throw new Error('BASE_URL must be configured by the harness.');
    }

    if (!value.startsWith('http://') && !value.startsWith('https://')) {
        throw new Error('BASE_URL must use http or https.');
    }

    return value.replace(/\/$/, '');
}
