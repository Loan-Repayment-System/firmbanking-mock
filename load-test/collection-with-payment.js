/**
 * 일괄 집금 중 지급·성명조회: 집금 동시 사용자 10명이 도는 동안 지급과 성명조회를 초당 5건씩 보낸다.
 * MODE=baseline이면 집금 없이 지급·성명조회만 같은 속도로 보낸다. 두 실행의 p95를 비교한다.
 * 성명조회는 잠금을 쓰지 않는 대조군이다.
 *
 * 지급 고객(seed.sql의 지급 고객 1,000명)은 한 번만 지급받는다. 워밍업은 1번부터, 측정은 201번부터 쓴다.
 * 성명조회는 지급 고객 계좌를 돌아가며 조회한다.
 */
import exec from 'k6/execution';
import { collection, nameInquiry, payment, send } from './lib/telegram.js';
import { scenarioThresholds, summarize, summaryTrendStats } from './lib/summary.js';

const HOST = __ENV.APP_HOST;
const PORT = Number(__ENV.APP_PORT || 9001);
const MODE = __ENV.MODE || 'main';
const WARMUP_SECONDS = Number(__ENV.WARMUP_SECONDS || 30);
const MEASURE_SECONDS = Number(__ENV.MEASURE_SECONDS || 120);
const RATE_PER_SECOND = 5;
const AMOUNT = 10000;
const COLLECTION_VUS = 10;
const PAYMENT_CUSTOMERS = 1000;
const MEASURED_PAYMENT_OFFSET = 200;
// 전문번호가 집금 고객 번호(최대 500000)와 겹치지 않게 지급·성명조회는 따로 시작한다.
const PAYMENT_MESSAGE_BASE = 600000;
const INQUIRY_MESSAGE_BASE = 700000;
const REAL_NAME_NO = '9001011234567';

if (!['main', 'baseline'].includes(MODE)) {
  throw new Error(`MODE는 main 또는 baseline이다: ${MODE}`);
}

const scenarios = {
  warmup_payment: rate('pay', 0, WARMUP_SECONDS, 0, MEASURED_PAYMENT_OFFSET),
  warmup_inquiry: rate('inquire', 0, WARMUP_SECONDS, 0, PAYMENT_CUSTOMERS),
  payment: rate('pay', WARMUP_SECONDS, MEASURE_SECONDS, MEASURED_PAYMENT_OFFSET, PAYMENT_CUSTOMERS),
  inquiry: rate('inquire', WARMUP_SECONDS, MEASURE_SECONDS, 0, PAYMENT_CUSTOMERS),
};
if (MODE === 'main') {
  scenarios.warmup_collection = collectionVus(0, WARMUP_SECONDS, 0);
  scenarios.collection = collectionVus(WARMUP_SECONDS, MEASURE_SECONDS, 100000);
}
const MEASURED = Object.keys(scenarios).filter((name) => !name.startsWith('warmup'));

// customerLimit: 이 시나리오가 쓸 수 있는 마지막 지급 고객 번호.
function rate(execName, startSeconds, durationSeconds, customerOffset, customerLimit) {
  return {
    executor: 'constant-arrival-rate',
    exec: execName,
    rate: RATE_PER_SECOND,
    timeUnit: '1s',
    preAllocatedVUs: 10,
    maxVUs: 50,
    startTime: `${startSeconds}s`,
    duration: `${durationSeconds}s`,
    env: { CUSTOMER_OFFSET: String(customerOffset), CUSTOMER_LIMIT: String(customerLimit) },
  };
}

function collectionVus(startSeconds, durationSeconds, customerOffset) {
  return {
    executor: 'constant-vus',
    exec: 'collect',
    vus: COLLECTION_VUS,
    startTime: `${startSeconds}s`,
    duration: `${durationSeconds}s`,
    env: { CUSTOMER_OFFSET: String(customerOffset) },
  };
}

export const options = {
  scenarios,
  thresholds: scenarioThresholds(MEASURED),
  summaryTrendStats,
};

export async function collect() {
  const iteration = exec.scenario.iterationInTest;
  if (iteration >= 100000) {
    exec.test.abort(`${exec.scenario.name}: 집금 고객 100000명을 다 썼다`);
  }
  const customerNo = Number(__ENV.CUSTOMER_OFFSET) + iteration + 1;
  const accountNo = `300-000-${String(customerNo).padStart(6, '0')}`;
  await send(HOST, PORT, collection(customerNo, accountNo, AMOUNT), 'collection', true);
}

export async function pay() {
  const customerNo = Number(__ENV.CUSTOMER_OFFSET) + exec.scenario.iterationInTest + 1;
  if (customerNo > Number(__ENV.CUSTOMER_LIMIT)) {
    exec.test.abort(`${exec.scenario.name}: 지급 고객 ${__ENV.CUSTOMER_LIMIT}번까지 다 썼다`);
  }
  const messageNo = PAYMENT_MESSAGE_BASE + customerNo;
  await send(HOST, PORT, payment(messageNo, paymentCustomerAccountNo(customerNo), AMOUNT), 'payment', true);
}

export async function inquire() {
  const iteration = exec.scenario.iterationInTest;
  const customerNo = (iteration % PAYMENT_CUSTOMERS) + 1;
  const messageNo = INQUIRY_MESSAGE_BASE + (iteration % 100000);
  await send(HOST, PORT, nameInquiry(messageNo, paymentCustomerAccountNo(customerNo), REAL_NAME_NO), 'inquiry', false);
}

function paymentCustomerAccountNo(customerNo) {
  return `400-000-${String(customerNo).padStart(6, '0')}`;
}

export function handleSummary(data) {
  const seconds = {};
  for (const name of MEASURED) {
    seconds[name] = MEASURE_SECONDS;
  }
  return summarize(data, seconds);
}
