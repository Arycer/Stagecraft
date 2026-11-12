import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

function redact(value, key = '') {
    if (/password|token|secret|authorization/i.test(key)) return '[redacted]';
    if (Array.isArray(value)) return value.map(entry => redact(entry));
    if (value && typeof value === 'object') {
        return Object.fromEntries(Object.entries(value).filter(([, entry]) => typeof entry !== 'function')
            .map(([name, entry]) => [name, redact(entry, name)]));
    }
    return value;
}

export class SessionReport {
    constructor(config) {
        this.id = crypto.randomBytes(8).toString('hex');
        this.started = new Date().toISOString();
        const folder = this.started.replace(/[:.]/g, '-') + '-' + this.id;
        this.dir = path.resolve(config.reportDir ?? 'build/director', folder);
        fs.mkdirSync(this.dir, { recursive: true });
        this.config = redact(config);
        this.sequence = 0;
        this.runs = [];
        this.state = 'starting';
        this.write();
    }

    event(type, fields = {}) {
        const event = { sequence: ++this.sequence, time: new Date().toISOString(), type, ...redact(fields) };
        fs.appendFileSync(path.join(this.dir, 'timeline.jsonl'), JSON.stringify(event) + '\n');
        return event;
    }

    write(snapshot = {}) {
        this.snapshot = { ...this.snapshot, ...snapshot };
        const document = {
            id: this.id, started: this.started, state: this.state,
            config: this.config, runs: this.runs, ...this.snapshot,
        };
        const file = path.join(this.dir, 'session.json');
        fs.writeFileSync(file + '.tmp', JSON.stringify(document, null, 2));
        fs.renameSync(file + '.tmp', file);
    }

    addRun(outcome) {
        const index = this.runs.length + 1;
        const file = path.join(this.dir, 'run-' + index + '-' + outcome.scenario + '.json');
        fs.writeFileSync(file, JSON.stringify(outcome, null, 2));
        this.runs.push({ scenario: outcome.scenario, passed: outcome.passed, file });
        this.write();
    }

    copyLog(source, name) {
        if (!source || !fs.existsSync(source)) return;
        fs.copyFileSync(source, path.join(this.dir, name));
    }
}
