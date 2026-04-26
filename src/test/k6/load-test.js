import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 50 }, // Ramp up to 50 users
        { duration: '1m', target: 50 },  // Stay at 50 users
        { duration: '30s', target: 0 },  // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% of requests should be below 500ms
        http_req_failed: ['rate<0.01'],   // Error rate should be less than 1%
    },
};

const BASE_URL = 'http://localhost:8080/api/v1';

export default function () {
    // 1. Fetch public events
    const eventsRes = http.get(`${BASE_URL}/events`);
    
    check(eventsRes, {
        'events status is 200': (r) => r.status === 200,
        'events returned fast': (r) => r.timings.duration < 500,
    });

    // We can simulate viewing the first event if events are returned
    if (eventsRes.status === 200) {
        try {
            const body = JSON.parse(eventsRes.body);
            if (body.content && body.content.length > 0) {
                const eventId = body.content[0].id;
                const eventDetailRes = http.get(`${BASE_URL}/events/${eventId}`);
                
                check(eventDetailRes, {
                    'event detail status is 200': (r) => r.status === 200,
                });
            }
        } catch (e) {
            // parsing error, ignore
        }
    }

    sleep(1);
}
