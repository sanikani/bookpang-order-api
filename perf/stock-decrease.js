import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:10366';
const strategy = (__ENV.STRATEGY || 'OPTIMISTIC').toUpperCase();
const scenarioName = __ENV.SCENARIO || 'high_tps_low_conflict';
const quantity = Number(__ENV.QUANTITY || '1');
const hotIsbnPrefix = __ENV.HOT_ISBN_PREFIX || 'HOT-ISBN-';
const hotCount = Number(__ENV.HOT_COUNT || '5');
const skewedHotShare = Number(__ENV.SKEWED_HOT_SHARE || '0.7');
const coldPrefix = __ENV.COLD_PREFIX || 'COLD-ISBN-';
const coldCount = Number(__ENV.COLD_COUNT || '100');
const scenarios = {
  high_tps_low_conflict: {
    executor: 'constant-arrival-rate',
    rate: 120,
    timeUnit: '1s',
    duration: '60s',
    preAllocatedVUs: 30,
    maxVUs: 100,
  },
  low_tps_high_conflict: {
    executor: 'constant-arrival-rate',
    rate: 15,
    timeUnit: '1s',
    duration: '60s',
    preAllocatedVUs: 10,
    maxVUs: 30,
  },
  high_tps_high_conflict: {
    executor: 'constant-arrival-rate',
    rate: 120,
    timeUnit: '1s',
    duration: '60s',
    preAllocatedVUs: 30,
    maxVUs: 100,
  },
  mixed_hybrid: {
    executor: 'constant-arrival-rate',
    rate: 80,
    timeUnit: '1s',
    duration: '60s',
    preAllocatedVUs: 20,
    maxVUs: 80,
  },
  skewed_conflict: {
    executor: 'constant-arrival-rate',
    rate: 120,
    timeUnit: '1s',
    duration: '60s',
    preAllocatedVUs: 30,
    maxVUs: 100,
  },
};

export const options = {
  scenarios: {
    stock_decrease: scenarios[scenarioName] || scenarios.high_tps_low_conflict,
  },
  thresholds: {
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<2000', 'p(99)<4000'],
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
    case 'low_tps_high_conflict':
    case 'high_tps_high_conflict':
      return hotIsbnAt(1);
    case 'mixed_hybrid':
      return __ITER % 5 === 0
        ? hotIsbnAt((__ITER % hotCount) + 1)
        : coldIsbnAt((__ITER % coldCount) + 1);
    case 'skewed_conflict': {
      const bucket = __ITER % 100;
      if (bucket < skewedHotShare * 100) {
        return hotIsbnAt((__ITER % hotCount) + 1);
      }
      return coldIsbnAt((__ITER % coldCount) + 1);
    }
    case 'high_tps_low_conflict':
    default:
      return coldIsbnAt((__ITER % coldCount) + 1);
  }
}

export default function () {
  const isbn = buildIsbn();
  const payload = JSON.stringify({
    isbn,
    quantity,
    strategy,
  });

  const response = http.post(`${baseUrl}/api/perf/stocks/decrease`, payload, {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      name: 'perf_stock_decrease',
      strategy: strategy.toLowerCase(),
      scenario: scenarioName,
    },
  });

  check(response, {
    'status is 200': (r) => r.status === 200,
  });

  if (response.status !== 200) {
    console.log(
      `[stock-decrease-failed] scenario=${scenarioName} strategy=${strategy} status=${response.status} body=${response.body}`
    );
  }
}
