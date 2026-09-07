// Ceiling probe — finds the point where the app STOPS coping, rather than confirming it copes.
//
// WHY THIS IS NOT capacity-ramp.js
// --------------------------------
// capacity-ramp.js ramps VIRTUAL USERS. That cannot find a ceiling reliably, because a VU is a
// closed loop: request -> wait for response -> sleep -> repeat. When the server slows down, each
// VU sends FEWER requests per second. The offered load quietly falls as the server degrades, the
// system self-limits, and the latency curve looks gracefully flat right up until it doesn't.
// That is why every recorded run in PERFORMANCE.md passed: they measured a system that was
// allowed to throttle its own inbound traffic.
//
// This script ramps ARRIVAL RATE (requests/second) instead. k6 keeps issuing requests on schedule
// no matter how slow the responses get, allocating more VUs to hold the rate. The queue actually
// builds, and you see the cliff.
//
// It also aborts the moment a threshold breaks, so the run stops AT the breaking point instead of
// averaging it away across the whole test.
//
// USAGE
// -----
//   Read path (no auth needed):
//     k6 run --env BASE_URL=http://localhost:8088 src/test/k6/ceiling-probe.js
//
//   Write path (needs a published event + a LARGE tier -- see the warning below):
//     k6 run --env BASE_URL=http://localhost:8088 --env PROBE=write \
//            --env AUTH_TOKEN=<jwt> --env EVENT_ID=<id> --env TIER_ID=<id> \
//            src/test/k6/ceiling-probe.js
//
//   Machine-readable output for plotting where p95 turned:
//     k6 run --out json=probe.json ... src/test/k6/ceiling-probe.js
//
// READING THE RESULT
// ------------------
// The number you want is the arrival rate of the LAST stage that passed. k6 prints the stage
// boundary times; cross-reference with the abort message.
//
// ⚠️ Check `dropped_iterations` before believing any result. If it is non-zero, k6 ran out of VUs
// and could not sustain the requested rate -- you measured the LOAD GENERATOR's limit, not the
// server's. Raise MAX_VUS and re-run.

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';

// Written into the container's mounted /results volume; `docker compose run --rm`
// discards anything left in k6's own working directory.
const OUT_DIR = __ENV.OUT_DIR || '/results';
const PROBE = (__ENV.PROBE || 'read').toLowerCase();

const AUTH_TOKEN = __ENV.AUTH_TOKEN;
const EVENT_ID = __ENV.EVENT_ID;
const TIER_ID = __ENV.TIER_ID;

// Raise this if dropped_iterations > 0. Each in-flight request needs a VU, so the requirement is
// roughly (arrival rate x p95 seconds). At 400 req/s and a degraded 2s p95 that is ~800 VUs.
const MAX_VUS = Number(__ENV.MAX_VUS || 2000);

export const serverErrors = new Counter('probe_server_errors');

// Arrival rate in requests/second, not concurrent users. The run is EXPECTED to abort partway
// through -- where it aborts is the answer.
//
// Derived from PEAK_RATE so the probe can be pushed higher without editing this file. Each stage
// doubles, so the default 800 reproduces the original 25/50/100/200/400/800 ladder exactly.
//
// If a run finishes every stage without aborting, the ceiling is ABOVE the top stage and the
// result is a FLOOR, not a limit -- re-run with a higher PEAK_RATE:
//
//   k6 run --env PEAK_RATE=2400 ceiling-probe.js
const PEAK_RATE = Number(__ENV.PEAK_RATE || 800);

const STAGES = [
    { duration: '1m', target: Math.round(PEAK_RATE / 32) },
    { duration: '2m', target: Math.round(PEAK_RATE / 16) },
    { duration: '2m', target: Math.round(PEAK_RATE / 8) },
    { duration: '2m', target: Math.round(PEAK_RATE / 4) },
    { duration: '2m', target: Math.round(PEAK_RATE / 2) },
    { duration: '2m', target: PEAK_RATE },
];

// The write path is slower by nature (lock + Lua + two DB writes), so it gets a looser latency
// budget. Both abort on breach -- that is the entire point of the script.
const LATENCY_THRESHOLD = PROBE === 'write' ? 'p(95)<800' : 'p(95)<500';

export const options = {
    scenarios: {
        ceiling: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 100,
            maxVUs: MAX_VUS,
            stages: STAGES,
            gracefulStop: '10s',
        },
    },
    thresholds: {
        // abortOnFail is what turns this from a pass/fail test into a measurement.
        http_req_duration: [{ threshold: LATENCY_THRESHOLD, abortOnFail: true, delayAbortEval: '10s' }],
        http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' }],
        probe_server_errors: [{ threshold: 'count==0', abortOnFail: true }],
        // Not a server property -- a load-generator property. Non-zero invalidates the run.
        dropped_iterations: ['count==0'],
    },
};

/**
 * k6 does not tag requests by stage, so derive it from elapsed time. Without this the summary is a
 * single run-wide aggregate that averages the healthy stages together with the failing one and
 * hides exactly the transition you are trying to find.
 */
function currentStageRate() {
    const elapsedSec = exec.instance.currentTestRunDuration / 1000;
    let boundary = 0;
    for (const stage of STAGES) {
        boundary += parseInt(stage.duration, 10) * (stage.duration.endsWith('m') ? 60 : 1);
        if (elapsedSec <= boundary) {
            return String(stage.target);
        }
    }
    return 'final';
}

function probeRead(tags) {
    const res = http.get(`${BASE_URL}/api/events`, { tags });
    check(res, { 'read: 200': (r) => r.status === 200 });
    if (res.status >= 500) {
        serverErrors.add(1);
    }
}

function probeWrite(tags) {
    const payload = JSON.stringify({
        eventId: Number(EVENT_ID),
        tierId: Number(TIER_ID),
        quantity: 1,
    });

    const res = http.post(`${BASE_URL}/api/v1/bookings`, payload, {
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${AUTH_TOKEN}`,
            // A FRESH key per request on purpose. Every iteration is a genuinely new reservation
            // intent, so reusing one would make the server return the same booking from cache and
            // measure the idempotency fast path instead of the reservation path.
            'Idempotency-Key': `probe-${exec.scenario.iterationInTest}-${Date.now()}`,
        },
        tags,
    });

    // 409 is a correct answer (sold out / contention), not a failure. Only 5xx is a failure.
    check(res, { 'write: 200 or 409': (r) => r.status === 200 || r.status === 409 });
    if (res.status >= 500) {
        serverErrors.add(1);
    }
}

export function setup() {
    if (PROBE === 'write' && (!AUTH_TOKEN || !EVENT_ID || !TIER_ID)) {
        throw new Error('PROBE=write requires AUTH_TOKEN, EVENT_ID and TIER_ID');
    }
    if (PROBE === 'write') {
        console.warn(
            '\n⚠️  WRITE PROBE: this creates REAL bookings and will drain the tier.\n' +
            '   Once the tier is empty every response is a cheap 409 and you are no longer\n' +
            '   measuring the reservation path at all -- you are measuring the sold-out path.\n' +
            '   Use a tier with totalCapacity far larger than (peak rate x run duration),\n' +
            '   e.g. 100000, on a throwaway event. Never point this at a real event.\n'
        );
    }
    return { probe: PROBE };
}

export default function () {
    // No sleep(). The arrival-rate executor controls pacing; a sleep here would fight it.
    const tags = { probe: PROBE, rate: currentStageRate() };

    if (PROBE === 'write') {
        probeWrite(tags);
    } else {
        probeRead(tags);
    }
}

export function handleSummary(data) {
    const dropped = data.metrics.dropped_iterations
        ? data.metrics.dropped_iterations.values.count
        : 0;
    const reqs = data.metrics.http_reqs.values.count;

    const lines = [
        '',
        '='.repeat(72),
        `CEILING PROBE (${PROBE}) — ${BASE_URL}`,
        '='.repeat(72),
        '',
        `  requests          : ${data.metrics.http_reqs.values.count}`,
        `  p95               : ${data.metrics.http_req_duration.values['p(95)'].toFixed(1)} ms`,
        `  failed            : ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)} %`,
        `  5xx               : ${data.metrics.probe_server_errors ? data.metrics.probe_server_errors.values.count : 0}`,
        `  dropped_iterations: ${dropped}`,
        '',
    ];

    // A binary "dropped > 0 means invalid" check is too blunt: a handful of drops while the
    // server is slowing down is a SYMPTOM of degradation, not proof the load generator ran out.
    // The question is whether k6 was actually starved, and peak VU usage answers it.
    const peakVUs = data.metrics.vus ? data.metrics.vus.values.max : 0;
    const attempted = reqs + dropped;
    const droppedPct = attempted > 0 ? (dropped / attempted) * 100 : 0;
    const vuHeadroom = MAX_VUS > 0 ? (peakVUs / MAX_VUS) * 100 : 0;

    lines.push(`  peak VUs          : ${peakVUs} of ${MAX_VUS} (${vuHeadroom.toFixed(0)}% of ceiling)`, '');

    if (vuHeadroom >= 95 || droppedPct > 5) {
        lines.push(
            '  ⚠️  RESULT INVALID — k6 was starved and could not sustain the arrival rate.',
            `     ${vuHeadroom >= 95 ? 'It hit the VU ceiling.' : 'It dropped ' + droppedPct.toFixed(1) + '% of iterations.'}`,
            '     You measured the load generator, not the server.',
            `     Re-run with --env MAX_VUS=${MAX_VUS * 2}`,
            ''
        );
    } else if (dropped > 0) {
        lines.push(
            `  ⚠️  AMBIGUOUS — ${dropped} iterations dropped (${droppedPct.toFixed(1)}%), but k6 still had`,
            `     ${MAX_VUS - peakVUs} VUs in reserve, so it was not starved. The drops are most likely a`,
            '     symptom of the server slowing, not the cause.',
            '',
            `     Confirm by re-running with --env MAX_VUS=${MAX_VUS * 2}. If the abort rate is`,
            '     roughly unchanged, the ceiling is real and this run stands.',
            ''
        );
    } else {
        lines.push(
            '  The ceiling is the arrival rate of the last stage that completed before the',
            '  abort. Stage targets (req/s): ' + STAGES.map((s) => s.target).join(' -> '),
            '  If the run finished all stages without aborting, the ceiling is ABOVE the top',
            '  stage and you should raise the stages rather than report the top one as a limit.',
            ''
        );
    }

    return {
        stdout: lines.join('\n'),
        [`${OUT_DIR}/probe-summary-${PROBE}.json`]: JSON.stringify(data, null, 2),
    };
}
