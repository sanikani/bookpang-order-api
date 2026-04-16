import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:10366';
const userId = __ENV.USER_ID || '100';
const expectedDeliveryDate = __ENV.EXPECTED_DELIVERY_DATE || '20271231';
const isbn = __ENV.ISBN || '9788901234567';
const orderPackagingId = Number(__ENV.ORDER_PACKAGING_ID || '1');
const amount = Number(__ENV.AMOUNT || '15000');
const quantity = Number(__ENV.QUANTITY || '1');
const stageProfile = __ENV.STAGE_PROFILE || 'baseline';

const profiles = {
  baseline: {
    executor: 'constant-arrival-rate',
    rate: 1,
    timeUnit: '1s',
    duration: '40s',
    preAllocatedVUs: 2,
    maxVUs: 5,
  },
  load5: {
    executor: 'constant-vus',
    vus: 5,
    duration: '60s',
  },
  load10: {
    executor: 'constant-vus',
    vus: 10,
    duration: '60s',
  },
  load20: {
    executor: 'constant-vus',
    vus: 20,
    duration: '60s',
  },
  load50: {
    executor: 'constant-vus',
    vus: 50,
    duration: '60s',
  },
  stock_conflict: {
    executor: 'constant-vus',
    vus: 20,
    duration: '45s',
  },
};

export const options = {
  scenarios: {
    order_create: profiles[stageProfile] || profiles.baseline,
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000', 'p(99)<4000'],
  },
};

function buildPayload() {
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;

  return JSON.stringify({
    ordererName: 'Performance User',
    ordererPhoneNumber: '010-1234-5678',
    ordererEmail: `perf-${suffix}@example.com`,
    recipientName: 'Performance Receiver',
    recipientPhoneNumber: '010-9876-5432',
    recipientEmail: `receiver-${suffix}@example.com`,
    recipientAddress: 'Seoul Gangnam-gu Teheran-ro 123',
    expectedDeliveryDate,
    orderCouponId: null,
    usedPoint: 0,
    items: [
      {
        orderPackagingId,
        isbn,
        qty: quantity,
        amount,
        bookCouponId: null,
        packagingRequest: true,
        categoryIds: [1],
      },
    ],
  });
}

export default function () {
  const response = http.post(`${baseUrl}/api/orders`, buildPayload(), {
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userId,
    },
    tags: {
      name: 'create_order',
      scenario: stageProfile,
    },
  });

  check(response, {
    'status is 200': (r) => r.status === 200,
    'contains orderNumber': (r) => r.body && r.body.includes('orderNumber'),
  });

  if (response.status !== 200) {
    console.log(
      `[order-create-failed] status=${response.status} body=${response.body}`
    );
  }

  sleep(1);
}
