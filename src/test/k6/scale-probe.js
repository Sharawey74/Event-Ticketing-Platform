// Scalability probe — measures whether N replicas do N× the work.
//
// HOW THIS DIFFERS FROM ceiling-probe.js
// --------------------------------------
//   ceiling-probe.js  ramps load until ONE instance breaks   -> finds a capacity limit
//   scale-probe.js    holds load CONSTANT across replica counts -> finds a scaling factor
//
// The load here is deliberately fixed. Changing both the load and the replica count at once
// gives you two variables and no conclusion. Run this three times — 1, 2, 4 replicas — with
// everything else identical, and the only thing that changed is the number of instances.
//
// USAGE (see docker-compose.scale.yml for the full loop)
//   REPLICAS=1 k6 run --env BASE_URL=http://lb:8088 src/test/k6/scale-probe.js
//   REPLICAS=2 ...
//   REPLICAS=4 ...
//
// READING THE RESULT
// ------------------
// Compare achieved throughput (http_reqs/s) across the three runs:
//
//   ~1.0x per replica   linear      — the tier scales; add instances to add capacity
//   ~0.6-0.9x           sub-linear  — a SHARED resource is the limit (expect Postgres here)
//   ~1.0x flat          none        — you are serialised somewhere, or the LB is not
//                                     distributing (check nginx re-resolution)
//   worse than 1        negative    — contention costs more than the added capacity buys
//
// ⚠️ Sub-linear is the NORMAL and expected result, not a failure. Perfect linear scaling means
// you have not yet found the shared bottleneck; it does not mean one is absent. The useful
// output is WHICH resource bends the curve, not whether the curve is straight.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';

// Written into the container's mounted /results volume; `docker compose run --rm`
// discards anything left in k6's own working directory.
const OUT_DIR = __ENV.OUT_DIR || '/results';

// Recorded purely so the summary is self-labelling — three runs otherwise produce three
// indistinguishable blocks of numbers.
const REPLICAS = __ENV.REPLICAS || 'unknown';

// FIXED for every run. Set it high enough to keep a single replica genuinely busy — if one
// replica absorbs this comfortably, adding more has nothing to prove and every run reports the
// same number. Raise it until the 1-replica run shows p95 climbing above idle.
const TARGET_RATE = Number(__ENV.TARGET_RATE || 200);
const DURATION = __ENV.DURATION || '3m';
const MAX_VUS = Number(__ENV.MAX_VUS || 1000);

export const serverErrors = new Counter('scale_server_errors');

export const options = {
    scenarios: {
        constant_load: {
            // Arrival-rate, not VUs, for the same reason as ceiling-probe.js: a VU-based test
            // sends fewer requests as the server slows, so the offered load would silently
            // differ between replica counts and the comparison would be meaningless.
            executor: 'constant-arrival-rate',
            rate: TARGET_RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 100,
            maxVUs: MAX_VUS,
        },
    },
    thresholds: {
        // No abortOnFail here. Unlike the ceiling probe, a run that degrades is still a valid
        // data point — degradation at 1 replica and recovery at 4 IS the finding.
        scale_server_errors: ['count==0'],
        dropped_iterations: ['count==0'],
    },
};

export default function () {
    const res = http.get(`${BASE_URL}/api/events`, { tags: { replicas: REPLICAS } });
    check(res, { 'status is 200': (r) => r.status === 200 });
    if (res.status >= 500) {
        serverErrors.add(1);
    }
}

export function handleSummary(data) {
    const reqs = data.metrics.http_reqs.values.count;
    const rps = data.metrics.http_reqs.values.rate;
    const p95 = data.metrics.http_req_duration.values['p(95)'];
    const failed = data.metrics.http_req_failed.values.rate * 100;
    const dropped = data.metrics.dropped_iterations
        ? data.metrics.dropped_iterations.values.count
        : 0;

    const lines = [
        '',
        '='.repeat(72),
        `SCALE PROBE — ${REPLICAS} replica(s) @ ${TARGET_RATE} req/s offered for ${DURATION}`,
        '='.repeat(72),
        '',
        `  achieved throughput : ${rps.toFixed(1)} req/s   <- COMPARE THIS ACROSS RUNS`,
        `  total requests      : ${reqs}`,
        `  p95                 : ${p95.toFixed(1)} ms`,
        `  failed              : ${failed.toFixed(2)} %`,
        `  5xx                 : ${data.metrics.scale_server_errors ? data.metrics.scale_server_errors.values.count : 0}`,
        `  dropped_iterations  : ${dropped}`,
        '',
    ];

    if (dropped > 0) {
        lines.push(
            '  ⚠️  RESULT INVALID — k6 could not sustain the offered rate, so this run did not',
            '     apply the same load as the others and is not comparable.',
            `     Re-run with --env MAX_VUS=${MAX_VUS * 2}`,
            ''
        );
    } else {
        lines.push(
            '  Scaling factor = (this throughput) / (1-replica throughput).',
            '  Record all three runs before drawing any conclusion — a single run in isolation',
            '  says nothing about scalability.',
            ''
        );
    }

    return {
        stdout: lines.join('\n'),
        [`${OUT_DIR}/scale-${REPLICAS}-replicas.json`]: JSON.stringify(data, null, 2),
    };
}
