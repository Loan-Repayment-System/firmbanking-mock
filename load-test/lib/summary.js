/**
 * 측정 시나리오별 TPS·응답 시간·처리 오류 비율을 results/{방법}/{실행}-summary.json에 남긴다.
 * 이름이 warmup으로 시작하는 시나리오는 집계하지 않는다.
 */

// k6는 threshold를 건 sub-metric만 summary에 따로 넣는다. 항상 통과하는 조건으로 시나리오별 값을 꺼낸다.
export function scenarioThresholds(names) {
  const thresholds = {};
  for (const name of names) {
    thresholds[`telegram_duration{scenario:${name}}`] = ['max>=0'];
    thresholds[`telegram_requests{scenario:${name}}`] = ['count>=0'];
    thresholds[`telegram_failed{scenario:${name}}`] = ['rate>=0'];
    thresholds[`tcp_socket_connecting{scenario:${name}}`] = ['max>=0'];
  }
  thresholds.transfer_success = ['count>=0'];
  thresholds.telegram_requests = ['count>=0'];
  return thresholds;
}

export const summaryTrendStats = ['avg', 'p(95)', 'p(99)', 'max'];

// seconds: 시나리오 이름 → 측정 시간(초). TPS = 요청 수 / 측정 시간.
export function summarize(data, seconds) {
  const scenarios = {};
  for (const [name, duration] of Object.entries(seconds)) {
    const requests = value(data, `telegram_requests{scenario:${name}}`, 'count');
    scenarios[name] = {
      requests,
      tps: round(requests / duration),
      avgMs: round(value(data, `telegram_duration{scenario:${name}}`, 'avg')),
      p95Ms: round(value(data, `telegram_duration{scenario:${name}}`, 'p(95)')),
      p99Ms: round(value(data, `telegram_duration{scenario:${name}}`, 'p(99)')),
      maxMs: round(value(data, `telegram_duration{scenario:${name}}`, 'max')),
      errorRate: value(data, `telegram_failed{scenario:${name}}`, 'rate'),
      connectP95Ms: round(value(data, `tcp_socket_connecting{scenario:${name}}`, 'p(95)')),
    };
  }
  const transferSuccess = value(data, 'transfer_success', 'count');
  const totalRequests = value(data, 'telegram_requests', 'count');
  const iterations = value(data, 'iterations', 'count');

  const dir = __ENV.RESULT_DIR;
  const run = __ENV.RUN;
  return {
    [`${dir}/${run}-summary.json`]: JSON.stringify({ run, scenarios, transferSuccess, totalRequests, iterations }, null, 2),
    // run.sh가 정확성 검사에 넘긴다. 워밍업 성공도 DB에 남으므로 포함한다.
    [`${dir}/${run}-transfer-success.txt`]: `${transferSuccess}\n`,
    stdout: table(scenarios) + `transfer_success=${transferSuccess}\n`,
  };
}

function value(data, metric, stat) {
  const m = data.metrics[metric];
  return m ? m.values[stat] : 0;
}

function round(v) {
  return Math.round(v * 100) / 100;
}

function table(scenarios) {
  const header = 'scenario\trequests\ttps\tp95Ms\tp99Ms\terrorRate\n';
  return header + Object.entries(scenarios)
    .map(([name, s]) => `${name}\t${s.requests}\t${s.tps}\t${s.p95Ms}\t${s.p99Ms}\t${s.errorRate}`)
    .join('\n') + '\n';
}
