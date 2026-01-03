import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const duplicateRate = new Rate('duplicates');
const latencyTrend = new Trend('latency_ms');

// More conservative load profile after initial tests crashed the system
export const options = {
    stages: [
        { duration: '1m', target: 20 },   // Ramp up slowly
        { duration: '2m', target: 20 },   // Sustain
        { duration: '30s', target: 0 },   // Ramp down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<1000', 'p(99)<2000'], // Target SLA
        'errors': ['rate<0.01'], // Less than 1% errors
    },
};

// Test accounts
const accounts = [
    'acc-001',
    'acc-002',
    'acc-003',
    'acc-004',
    'acc-005',
];

let transactionCounter = 0;

export default function () {
    const fromAccount = accounts[Math.floor(Math.random() * accounts.length)];
    let toAccount = accounts[Math.floor(Math.random() * accounts.length)];
    
    // Ensure from and to are different
    while (toAccount === fromAccount) {
        toAccount = accounts[Math.floor(Math.random() * accounts.length)];
    }
    
    // Unique idempotency key per transaction
    const idempotencyKey = `txn-loadtest-${Date.now()}-${transactionCounter++}`;
    
    const payload = JSON.stringify({
        eventId: generateUUID(),
        transactionId: generateUUID(),
        fromAccountId: fromAccount,
        toAccountId: toAccount,
        amount: Math.random() * 100 + 10, // $10-$110
        currency: 'USD',
        type: 'PAYMENT',
        timestamp: new Date().toISOString(),
        idempotencyKey: idempotencyKey
    });
    
    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };
    
    const res = http.post('http://localhost:8081/api/v1/transactions', payload, params);
    
    const success = check(res, {
        'status is 200 or 409': (r) => r.status === 200 || r.status === 409,
        'has transactionId': (r) => r.json('transactionId') !== undefined,
    });
    
    errorRate.add(!success);
    
    if (res.status === 409) {
        duplicateRate.add(true);
    } else {
        duplicateRate.add(false);
    }
    
    // Longer sleep because transactions are heavier
    sleep(0.5);
}

function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}
