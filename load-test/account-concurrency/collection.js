/**
 * 일괄 집금: 서로 다른 고객이 수납 계좌로 1만 원씩 집금한다. 함께 갱신하는 행은 수납 계좌 하나다.
 * 워밍업 30초 뒤 동시 사용자 10 → 20 → 50 → 100을 단계마다 1분씩 유지한다(램프업 없음).
 *
 * 고객 한 명은 한 번만 집금한다. 시나리오마다 고객 번호 구간을 10만 개씩 나눠 쓰고(seed.sql의 집금 고객 50만 명),
 * 고객 번호를 전문번호로도 쓴다.
 */
import exec from 'k6/execution';
import { collection, send } from './lib/telegram.js';
import { scenarioThresholds, summarize, summaryTrendStats } from './lib/summary.js';

const HOST = __ENV.APP_HOST;
const PORT = Number(__ENV.APP_PORT || 9001);
const WARMUP_SECONDS = Number(__ENV.WARMUP_SECONDS || 30);
const STAGE_SECONDS = Number(__ENV.STAGE_SECONDS || 60);
const AMOUNT = 10000;
const CUSTOMERS_PER_SCENARIO = 100000;

const STAGES = [10, 20, 50, 100];
const MEASURED = STAGES.map((vus) => `vus_${vus}`);

const scenarios = {
  warmup: stage(10, 0, WARMUP_SECONDS, 0),
};
STAGES.forEach((vus, i) => {
  scenarios[`vus_${vus}`] = stage(vus, WARMUP_SECONDS + i * STAGE_SECONDS, STAGE_SECONDS, i + 1);
});

function stage(vus, startSeconds, durationSeconds, customerBlock) {
  return {
    executor: 'constant-vus',
    exec: 'collect',
    vus,
    startTime: `${startSeconds}s`,
    duration: `${durationSeconds}s`,
    env: { CUSTOMER_OFFSET: String(customerBlock * CUSTOMERS_PER_SCENARIO) },
  };
}

export const options = {
  scenarios,
  thresholds: scenarioThresholds(MEASURED),
  summaryTrendStats,
};

export async function collect() {
  const iteration = exec.scenario.iterationInTest;
  if (iteration >= CUSTOMERS_PER_SCENARIO) {
    exec.test.abort(`${exec.scenario.name}: 고객 ${CUSTOMERS_PER_SCENARIO}명을 다 썼다`);
  }
  const customerNo = Number(__ENV.CUSTOMER_OFFSET) + iteration + 1;
  await send(HOST, PORT, collection(customerNo, customerAccountNo(customerNo), AMOUNT), 'collection', true);
}

function customerAccountNo(customerNo) {
  return `300-000-${String(customerNo).padStart(6, '0')}`;
}

export function handleSummary(data) {
  const seconds = {};
  for (const name of MEASURED) {
    seconds[name] = STAGE_SECONDS;
  }
  return summarize(data, seconds);
}
