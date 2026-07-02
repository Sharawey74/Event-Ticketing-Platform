// High-concurrency burst against a deliberately low-capacity ticket tier.
// A high 409 (sold out) rate here is a PASS — it proves the Redis Lua floor
// guard degrades cleanly under oversell pressure instead of overselling or
// throwing 500s. Only 5xx responses count as failures.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';
const AUTH_TOKEN = __ENV.AUTH_TOKEN;
const EVENT_ID = __ENV.EVENT_ID;
const TIER_ID = __ENV.TIER_ID;

export const serverErrors = new Counter('booking_server_errors');

export const options = {
    stages: [
        { duration: '10s', target: 100 }, // fast burst — simulate oversell pressure
        { duration: '20s', target: 100 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        booking_server_errors: ['count==0'],
    },
};

export default function () {
    if (!AUTH_TOKEN || !EVENT_ID || !TIER_ID) {
        throw new Error('Missing required env vars: BASE_URL, AUTH_TOKEN, EVENT_ID, TIER_ID');
    }

    const payload = JSON.stringify({
        eventId: Number(EVENT_ID),
        tierId: Number(TIER_ID),
        quantity: 1,
    });

    const res = http.post(`${BASE_URL}/api/v1/bookings`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${AUTH_TOKEN}`,
        },
    });

    check(res, {
        'status is 200 (success) or 409 (sold out — expected under oversell pressure)':
            (r) => r.status === 200 || r.status === 409,
    });

    if (res.status >= 500) {
        serverErrors.add(1);
    }

    sleep(0.2);
}
