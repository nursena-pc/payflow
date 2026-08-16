const DEFAULT_FIXTURE_FILE = '/work/fixtures/credential-pool.example.json';
const PLACEHOLDER_PREFIX = 'replace-runtime-';

export function loadFixtureDocument() {
    const path = (__ENV.K6_FIXTURE_FILE || DEFAULT_FIXTURE_FILE).trim();

    if (!path.startsWith('/work/') && !path.startsWith('/results/')) {
        throw new Error('K6_FIXTURE_FILE must be mounted under /work or /results.');
    }

    return JSON.parse(open(path));
}

export function fixtureSection(document, name) {
    const section = document[name];

    if (!Array.isArray(section) || section.length === 0) {
        throw new Error(`Fixture section ${name} must contain at least one item.`);
    }

    return section;
}

export function assertFixtureCapacity(section, required) {
    if (section.length < required) {
        throw new Error(
            `Fixture pool contains ${section.length} items but ${required} are required.`
        );
    }
}

export function fixtureForIteration(section, index, fields) {
    const fixture = section[index];

    if (!fixture) {
        throw new Error(`No credential fixture exists for iteration ${index}.`);
    }

    for (const field of fields) {
        const value = fixture[field];

        if (typeof value !== 'string' || value.trim().length === 0) {
            throw new Error(`Credential fixture field ${field} is required.`);
        }

        if (value.startsWith(PLACEHOLDER_PREFIX)) {
            throw new Error(
                `Credential fixture field ${field} still contains an example placeholder.`
            );
        }
    }

    return fixture;
}
