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
        { duration: '30s', target: 20 },
        { duration: '1m', target: 20 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        booking_server_errors: ['count==0'],
        http_req_duration: ['p(95)<800'],
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
        'status is 200 (success) or 409 (sold out — expected under load)':
            (r) => r.status === 200 || r.status === 409,
    });

    if (res.status >= 500) {
        serverErrors.add(1);
    }

    sleep(1);
}
