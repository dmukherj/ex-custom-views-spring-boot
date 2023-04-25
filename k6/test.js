import http from 'k6/http';
import { check, group, fail, sleep } from 'k6';
import { createUrl, fetchUrl, getHeaderWithToken } from './HttpHelper.js';
import { randomItem, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

const currency = ['USD', 'GBP', 'JPY', 'EUR'];

const TEST_2_RUN = __ENV.TEST_2_RUN || 'load';

const issuerParty = __ENV.ISSUER_PARTY;

const ExecutionType = {
  test: 'test',
  load: 'load',
};

let ExecutionOptions_Scenarios;
const Execution = TEST_2_RUN;

switch (Execution) {
  case ExecutionType.load:
    ExecutionOptions_Scenarios = {
      CreateIou_Scenario: {
        exec: 'createIouLoad',
        executor: 'shared-iterations',
        iterations: 10000,
        maxDuration: '60m',
        vus: 20
      }
    }
  break;
  case ExecutionType.test:
    ExecutionOptions_Scenarios = {
      CreateIou_Scenario: {
        exec: 'createIouTest',
        executor: 'constant-vus',
        vus: 1,
        duration: '360m',
      }
    }
  break;
}

export let options = {
  scenarios: ExecutionOptions_Scenarios,
  setupTimeout: '30s'
}

export function setup() {

  let params = getHeaderWithToken(issuerParty);

  return params;
}

export function createIouLoad(params) {

  let iouCid;
  group('Create iou', () => {
    const res = http.post(createUrl, JSON.stringify(issueIouContract(issuerParty)), params);
    if (check(res, { 'Created iou successfully': r => r.status === 200 })) {
      iouCid = res.json().result.contractId;
    } else {
      fail(`Failed to create iou ${JSON.stringify(res.body)}`);
    }
  });

  sleep(1);
}

export function createIouTest(params) {

  let iouCid;
  group('Create iou', () => {
    const res = http.post(createUrl, JSON.stringify(issueIouContract(issuerParty)), params);
    if (check(res, { 'Created iou successfully': r => r.status === 200 })) {
      iouCid = res.json().result.contractId;
      console.log('Created iou cid ' + iouCid)
    } else {
      fail(`Failed to create iou ${JSON.stringify(res.body)}`);
    }
  });

  const sleepDuration = 60 * __ITER;
  console.log('Sleep for ' + sleepDuration/60 + 'm')
  sleep(sleepDuration);
}

function issueIouContract(issuerParty) {

  return {
    "templateId": `Iou:Iou`,
    "payload": {
      "issuer": issuerParty,
      "owner": issuerParty,
      "currency": randomItem(currency),
      "amount": randomIntBetween(10, 99),
      "observers": []
    }
  }
}
