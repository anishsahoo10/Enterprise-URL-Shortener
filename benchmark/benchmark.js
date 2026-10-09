import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// Custom metrics
const redirectLatency = new Trend('redirect_latency_ms');
const errorRate = new Rate('redirect_error_rate');
const successCounter = new Counter('redirect_success_count');

export const options = {
  stages: [
    { duration: '5s', target: 20 },  // Warm-up ramp
    { duration: '30s', target: 50 }, // Sustained load
    { duration: '5s', target: 0 },   // Cool-down ramp
  ],
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'redirect_latency_ms': ['p(95)<100'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHORT_CODE = __ENV.SHORT_CODE || 'testCode';

export default function () {
  const params = {
    redirects: 0, // Measure redirect itself, don't follow target
    headers: {
      'User-Agent': 'k6-load-benchmark',
      'Referer': 'https://benchmark.internal',
    },
  };

  const res = http.get(`${BASE_URL}/${SHORT_CODE}`, params);

  const isSuccess = check(res, {
    'status is 302': (r) => r.status === 302,
    'has Location header': (r) => r.headers['Location'] !== undefined,
  });

  redirectLatency.add(res.timings.duration);
  errorRate.add(!isSuccess);
  if (isSuccess) {
    successCounter.add(1);
  }

  sleep(0.01);
}
