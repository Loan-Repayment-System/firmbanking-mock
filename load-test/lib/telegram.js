import { Socket } from 'k6/x/tcp';
import { Counter, Rate, Trend } from 'k6/metrics';

// 요청·응답 모두 길이 헤더 "0300" + 본문 300바이트다.
const BODY_LENGTH = 300;
const HEADER = '0300';
const MESSAGE_LENGTH = HEADER.length + BODY_LENGTH;
const RESPONSE_CODE = { offset: 52, length: 4 };

export const COMPANY_NO = 'CAPITAL00001';
export const BANK_CODE = '999';
export const COLLECTION_ACCOUNT_NO = '100-000-000001';
export const PAYMENT_ACCOUNT_NO = '100-000-000002';
export const SUCCESS = '0000';

// 연결 시작부터 응답 304바이트를 다 받을 때까지.
export const telegramDuration = new Trend('telegram_duration', true);
export const telegramRequests = new Counter('telegram_requests');
// 응답코드가 0000이 아니거나 응답을 받지 못한 비율.
export const telegramFailed = new Rate('telegram_failed');
// 정확성 검사에서 성공한 이체 결과 행 수와 비교한다. 성명조회는 이체 결과를 남기지 않아 세지 않는다.
export const transferSuccess = new Counter('transfer_success');

// 공통부·개별부 필드 위치는 src/main/java/com/firmbanking/telegram/codec/CommonField와 controller/field/와 같다.
const Common = {
  COMPANY_NO: { offset: 9, length: 12 },
  BANK_CODE: { offset: 21, length: 3 },
  TELEGRAM_TYPE_CODE: { offset: 24, length: 4 },
  BUSINESS_TYPE_CODE: { offset: 28, length: 3 },
  SEND_COUNT: { offset: 31, length: 1 },
  TELEGRAM_NO: { offset: 32, length: 6 },
  SEND_DATE: { offset: 38, length: 8 },
  SEND_TIME: { offset: 46, length: 6 },
};

// 지급 2000/100과 집금 2000/200은 메모 위치만 다르다.
const Transfer = {
  WITHDRAWAL_ACCOUNT_NO: { offset: 100, length: 15 },
  AMOUNT: { offset: 129, length: 13 },
  DEPOSIT_BANK_CODE: { offset: 156, length: 3 },
  DEPOSIT_ACCOUNT_NO: { offset: 159, length: 15 },
};
const PAYMENT_MEMO = { offset: 199, length: 14 };
const COLLECTION_MEMO = { offset: 183, length: 14 };

const NameInquiry = {
  ACCOUNT_NO: { offset: 100, length: 15 },
  REAL_NAME_NO: { offset: 115, length: 13 },
  BANK_CODE: { offset: 140, length: 3 },
  AMOUNT: { offset: 143, length: 13 },
};

const MEMO = 'LOADTEST';

export function collection(messageNo, withdrawalAccountNo, amount) {
  return transferBody('200', messageNo, withdrawalAccountNo, COLLECTION_ACCOUNT_NO, amount, COLLECTION_MEMO);
}

export function payment(messageNo, depositAccountNo, amount) {
  return transferBody('100', messageNo, PAYMENT_ACCOUNT_NO, depositAccountNo, amount, PAYMENT_MEMO);
}

export function nameInquiry(messageNo, accountNo, realNameNo) {
  const body = commonPart('6000', '100', messageNo);
  put(body, NameInquiry.ACCOUNT_NO, accountNo);
  put(body, NameInquiry.REAL_NAME_NO, realNameNo);
  put(body, NameInquiry.BANK_CODE, BANK_CODE);
  put(body, NameInquiry.AMOUNT, number(0, NameInquiry.AMOUNT.length));
  return body;
}

function transferBody(businessTypeCode, messageNo, withdrawalAccountNo, depositAccountNo, amount, memoField) {
  const body = commonPart('2000', businessTypeCode, messageNo);
  put(body, Transfer.WITHDRAWAL_ACCOUNT_NO, withdrawalAccountNo);
  put(body, Transfer.AMOUNT, number(amount, Transfer.AMOUNT.length));
  put(body, Transfer.DEPOSIT_BANK_CODE, BANK_CODE);
  put(body, Transfer.DEPOSIT_ACCOUNT_NO, depositAccountNo);
  put(body, memoField, MEMO);
  return body;
}

function commonPart(telegramTypeCode, businessTypeCode, messageNo) {
  const body = new Uint8Array(BODY_LENGTH).fill(0x20);
  // 서버는 전송일자·시각이 실제 날짜·시각인지만 본다. KST로 넣는다.
  const kst = new Date(Date.now() + 9 * 60 * 60 * 1000).toISOString();
  put(body, Common.COMPANY_NO, COMPANY_NO);
  put(body, Common.BANK_CODE, BANK_CODE);
  put(body, Common.TELEGRAM_TYPE_CODE, telegramTypeCode);
  put(body, Common.BUSINESS_TYPE_CODE, businessTypeCode);
  put(body, Common.SEND_COUNT, '1');
  put(body, Common.TELEGRAM_NO, number(messageNo, Common.TELEGRAM_NO.length));
  put(body, Common.SEND_DATE, kst.slice(0, 10).replace(/-/g, ''));
  put(body, Common.SEND_TIME, kst.slice(11, 19).replace(/:/g, ''));
  return body;
}

// 요청 값은 모두 ASCII라 EUC-KR 변환 없이 한 글자를 한 바이트로 넣는다.
function put(body, field, value) {
  for (let i = 0; i < value.length; i++) {
    body[field.offset + i] = value.charCodeAt(i);
  }
}

function number(value, length) {
  return String(value).padStart(length, '0');
}

/**
 * 전문 하나를 보내고 응답 시간과 성공 여부를 지표로 기록한다. 거래마다 연결을 새로 열고, 서버가 응답을 쓴 뒤 연결을 닫는다.
 * 응답을 다 받지 못하면 실패로 기록한다.
 */
export async function send(host, port, body, transaction, isTransfer) {
  const message = new Uint8Array(MESSAGE_LENGTH);
  for (let i = 0; i < HEADER.length; i++) {
    message[i] = HEADER.charCodeAt(i);
  }
  message.set(body, HEADER.length);

  const received = new Uint8Array(MESSAGE_LENGTH);
  let receivedLength = 0;
  let duration = null;
  const socket = new Socket();
  // k6는 등록된 타이머·콜백이 없으면 Promise가 끝나지 않았어도 iteration을 끝낸다.
  // k6/x/tcp는 응답을 기다리는 동안 콜백을 등록해 두지 않을 때가 있어, 응답이 늦게 오면 지표를 남기기 전에 iteration이 끝난다.
  // 30초 타이머를 등록해 응답·close·error 중 하나가 올 때까지 iteration이 끝나지 않게 한다. 앱은 잠금 대기 10초 안에 응답한다.
  let finish;
  const done = new Promise((resolve) => {
    finish = resolve;
  });
  const guard = setTimeout(() => {
    socket.destroy();
    finish();
  }, 30000);
  socket.on('data', (data) => {
    const chunk = new Uint8Array(data);
    received.set(chunk.subarray(0, MESSAGE_LENGTH - receivedLength), receivedLength);
    receivedLength = Math.min(MESSAGE_LENGTH, receivedLength + chunk.length);
    if (receivedLength === MESSAGE_LENGTH && duration === null) {
      duration = Date.now() - start;
      socket.destroy();
      finish();
    }
  });
  socket.on('close', () => finish());
  socket.on('error', () => finish());

  const start = Date.now();
  try {
    await socket.connect(port, host);
    await socket.write(message.buffer);
  } catch (e) {
    socket.destroy();
    finish();
  }
  await done;
  clearTimeout(guard);

  const code = receivedLength === MESSAGE_LENGTH
    ? String.fromCharCode(...received.subarray(HEADER.length + RESPONSE_CODE.offset,
        HEADER.length + RESPONSE_CODE.offset + RESPONSE_CODE.length))
    : 'NONE';
  const tags = { transaction };
  telegramRequests.add(1, tags);
  telegramFailed.add(code !== SUCCESS, tags);
  if (duration !== null) {
    telegramDuration.add(duration, tags);
  }
  if (isTransfer && code === SUCCESS) {
    transferSuccess.add(1, tags);
  }
}
