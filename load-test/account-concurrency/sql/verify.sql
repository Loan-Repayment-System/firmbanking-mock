-- 측정 뒤 잔액과 이체 결과가 맞는지 확인한다. run.sh가 먼저 넣는 값:
--   @k6_transfer_success: k6가 받은 집금·지급 성공 응답 수
--   @k6_transfer_system_error: k6가 받은 집금·지급 시스템 오류 응답 수

SET @collection_success_sum = (
    SELECT COALESCE(SUM(amount), 0) FROM transfer WHERE transfer_type_id = 2 AND response_code = '0000');
SET @payment_success_sum = (
    SELECT COALESCE(SUM(amount), 0) FROM transfer WHERE transfer_type_id = 1 AND response_code = '0000');
SET @collection_balance = (
    SELECT balance FROM account WHERE bank_code = '999' AND account_no = '100-000-000001');
SET @payment_decrease = 10000000000 - (
    SELECT balance FROM account WHERE bank_code = '999' AND account_no = '100-000-000002');
-- 처음 잔액 합계: 지급 계좌 100억 + 집금 고객 1만씩. 수납 계좌와 지급 고객은 0이다.
SET @initial_total = 10000000000 + (
    SELECT COUNT(*) * 10000 FROM account WHERE bank_code = '999' AND account_no LIKE '300-%');
SET @balance_total = (SELECT SUM(balance) FROM account);
SET @success_rows = (SELECT COUNT(*) FROM transfer WHERE response_code = '0000');
SET @odd_collection_customers = (
    SELECT COUNT(*) FROM account
    WHERE bank_code = '999' AND account_no LIKE '300-%' AND balance NOT IN (0, 10000));
SET @odd_payment_customers = (
    SELECT COUNT(*) FROM account
    WHERE bank_code = '999' AND account_no LIKE '400-%' AND balance NOT IN (0, 10000));
-- 처음 잔액은 거래내역이 없으므로 위 시드 규칙으로 계산한다. 거래 유형 1 출금, 2 입금.
SET @transaction_mismatch_accounts = (
    SELECT COUNT(*)
    FROM account a
    LEFT JOIN (
        SELECT account_id,
               SUM(IF(account_transaction_type_id = 2, amount, 0))
                   - SUM(IF(account_transaction_type_id = 1, amount, 0)) net
        FROM account_transaction
        GROUP BY account_id
    ) t ON t.account_id = a.account_id
    WHERE CASE
              WHEN a.account_no = '100-000-000002' THEN 10000000000
              WHEN a.account_no LIKE '300-%' THEN 10000
              ELSE 0
          END + COALESCE(t.net, 0) <> a.balance);
-- 요청 상태 1 처리 중, 3 오류. 이체가 끝나지 않은 요청은 시스템 오류로 응답한다.
SET @unfinished_requests = (
    SELECT COUNT(*) FROM transfer_request WHERE transfer_request_status_id IN (1, 3));

SELECT item, CAST(expected AS SIGNED) AS expected, CAST(actual AS SIGNED) AS actual,
       IF(expected = actual, 'PASS', 'FAIL') AS result
FROM (
    SELECT '수납 잔액 = 성공한 집금 합계' AS item, @collection_success_sum AS expected, @collection_balance AS actual
    UNION ALL
    SELECT '지급 계좌 감소 = 성공한 지급 합계', @payment_success_sum, @payment_decrease
    UNION ALL
    SELECT '잔액 합계 보존', @initial_total, @balance_total
    UNION ALL
    SELECT '성공한 이체 결과 행 수 = k6 성공 응답 수', @k6_transfer_success, @success_rows
    UNION ALL
    SELECT '잔액이 0·1만이 아닌 집금 고객 수', 0, @odd_collection_customers
    UNION ALL
    SELECT '잔액이 0·1만이 아닌 지급 고객 수', 0, @odd_payment_customers
    UNION ALL
    SELECT '처음 잔액 + 거래내역 합계 ≠ 잔액인 계좌 수', 0, @transaction_mismatch_accounts
    UNION ALL
    SELECT '처리 중·오류 요청 기록 수 = k6 시스템 오류 응답 수', @k6_transfer_system_error, @unfinished_requests
) checks;
