import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// Unlike the other 3 scripts (which default to localhost), this one defaults to the live
// Railway URL — its whole purpose is establishing a real capacity number for the actual
// deployment (1 replica, 5-connection Hikari pool), not a local dev-machine baseline.
const BASE_URL = __ENV.BASE_URL || 'https://backend-production-8daea.up.railway.app';
const AUTH_TOKEN = __ENV.AUTH_TOKEN;
const EVENT_ID = __ENV.EVENT_ID;
const TIER_ID = __ENV.TIER_ID;

export const serverErrors = new Counter('capacity_server_errors');

export const options = {
    stages: [
        { duration: '2m', target: 10 },
        { duration: '3m', target: 25 },
        { duration: '3m', target: 50 },
        { duration: '3m', target: 100 },
        { duration: '3m', target: 200 },
        { duration: '3m', target: 500 },
        { duration: '3m', target: 1000 },
        { duration: '2m', target: 0 },
    ],
    thresholds: {
        capacity_server_errors: ['count==0'],
        'http_req_duration{journey:browse}': ['p(95)<500'],
        'http_req_duration{journey:search}': ['p(95)<500'],
        'http_req_duration{journey:my-bookings}': ['p(95)<500'],
        'http_req_duration{journey:reserve}': ['p(95)<800'],
    },
};

function browseEvents() {
    const res = http.get(`${BASE_URL}/api/events`, { tags: { journey: 'browse' } });
    check(res, { 'browse: status is 200': (r) => r.status === 200 });
    if (res.status >= 500) {
        serverErrors.add(1);
    }
}

function searchEvents() {
    const res = http.get(`${BASE_URL}/api/search/events?q=concert`, { tags: { journey: 'search' } });
    check(res, { 'search: status is 200': (r) => r.status === 200 });
    if (res.status >= 500) {
        serverErrors.add(1);
    }
}

function reserveTicket() {
    if (!AUTH_TOKEN || !EVENT_ID || !TIER_ID) {
        return; // reserve/my-bookings journeys are skipped if credentials weren't supplied
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
        tags: { journey: 'reserve' },
    });

    check(res, {
        'reserve: status is 200 (success) or 409 (sold out / contention — expected under load)':
            (r) => r.status === 200 || r.status === 409,
    });

    if (res.status >= 500) {
        serverErrors.add(1);
    }
}

function checkMyBookings() {
    if (!AUTH_TOKEN) {
        return;
    }

    const res = http.get(`${BASE_URL}/api/v1/bookings/my`, {
        headers: { 'Authorization': `Bearer ${AUTH_TOKEN}` },
        tags: { journey: 'my-bookings' },
    });

    check(res, { 'my-bookings: status is 200': (r) => r.status === 200 });

    if (res.status >= 500) {
        serverErrors.add(1);
    }
}

// Weighted realistic journey mix: 40% browse, 20% search, 25% reserve, 15% check own bookings.
export default function () {
    const roll = Math.random();

    if (roll < 0.40) {
        browseEvents();
    } else if (roll < 0.60) {
        searchEvents();
    } else if (roll < 0.85) {
        reserveTicket();
    } else {
        checkMyBookings();
    }

    sleep(1);
}
