import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const orderHotDuration = new Trend('order_hot_duration', true);
const orderColdDuration = new Trend('order_cold_duration', true);
const orderHotFailed = new Rate('order_hot_failed');
const orderColdFailed = new Rate('order_cold_failed');
const orderHotCount = new Counter('order_hot_count');
const orderColdCount = new Counter('order_cold_count');

const baseUrl = __ENV.BASE_URL || 'http://localhost:10366';
const strategy = (__ENV.STRATEGY || 'OPTIMISTIC').toUpperCase();
const scenarioName = __ENV.SCENARIO || 'order_low_conflict';
const quantity = Number(__ENV.QUANTITY || '1');
const amount = Number(__ENV.AMOUNT || '15000');
const hotIsbnPrefix = __ENV.HOT_ISBN_PREFIX || 'HOT-ISBN-';
const hotCount = Number(__ENV.HOT_COUNT || '5');
const skewedHotShare = Number(__ENV.SKEWED_HOT_SHARE || '0.7');
const coldPrefix = __ENV.COLD_PREFIX || 'COLD-ISBN-';
const coldCount = Number(__ENV.COLD_COUNT || '100');
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || '80');
const maxVUs = Number(__ENV.MAX_VUS || '300');

const scenarios = {
  order_low_conflict: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || '120'),
    timeUnit: '1s',
    duration: __ENV.DURATION || '60s',
    preAllocatedVUs,
    maxVUs,
  },
  order_high_conflict: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || '120'),
    timeUnit: '1s',
    duration: __ENV.DURATION || '60s',
    preAllocatedVUs,
    maxVUs,
  },
  order_skewed_conflict: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || '120'),
    timeUnit: '1s',
    duration: __ENV.DURATION || '60s',
    preAllocatedVUs,
    maxVUs,
  },
};

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    order_create: scenarios[scenarioName] || scenarios.order_low_conflict,
  },
  thresholds: {
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<3000', 'p(99)<5000'],
  },
};

function hotIsbnAt(index) {
  return `${hotIsbnPrefix}${String(index).padStart(4, '0')}`;
}

function coldIsbnAt(index) {
  return `${coldPrefix}${String(index).padStart(4, '0')}`;
}

function buildIsbn() {
  switch (scenarioName) {
    case 'order_high_conflict':
      return hotIsbnAt(1);
    case 'order_skewed_conflict': {
      if (Math.random() < skewedHotShare) {
        return hotIsbnAt(Math.floor(Math.random() * hotCount) + 1);
      }
      return coldIsbnAt(Math.floor(Math.random() * coldCount) + 1);
    }
    case 'order_low_conflict':
    default:
      return coldIsbnAt(Math.floor(Math.random() * coldCount) + 1);
  }
}

function isbnType(isbn) {
  return isbn.startsWith(hotIsbnPrefix) ? 'hot' : 'cold';
}

function expectedDeliveryDate() {
  const date = new Date();
  date.setDate(date.getDate() + 7);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}${month}${day}`;
}

export default function () {
  const isbn = buildIsbn();
  const payload = JSON.stringify({
    ordererName: 'Benchmark User',
    ordererPhoneNumber: '010-1234-5678',
    ordererEmail: 'benchmark@example.com',
    recipientName: 'Benchmark User',
    recipientPhoneNumber: '010-1234-5678',
    recipientEmail: 'benchmark@example.com',
    recipientAddress: 'Seoul',
    expectedDeliveryDate: expectedDeliveryDate(),
    usedPoint: 0,
    items: [
      {
        isbn,
        qty: quantity,
        amount,
        packagingRequest: false,
        categoryIds: [],
      },
    ],
  });

  const response = http.post(`${baseUrl}/api/orders`, payload, {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      name: 'order_create',
      strategy: strategy.toLowerCase(),
      scenario: scenarioName,
      isbn_type: isbnType(isbn),
    },
  });

  const type = isbnType(isbn);
  const failed = response.status !== 200;
  if (type === 'hot') {
    orderHotDuration.add(response.timings.duration);
    orderHotFailed.add(failed);
    orderHotCount.add(1);
  } else {
    orderColdDuration.add(response.timings.duration);
    orderColdFailed.add(failed);
    orderColdCount.add(1);
  }

  check(response, {
    'status is 200': (r) => r.status === 200,
  });

  void response;
}
