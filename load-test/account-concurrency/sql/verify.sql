-- 측정 뒤 잔액과 이체 결과가 맞는지 확인한다. run.sh가 @k6_transfer_success(k6가 받은 집금·지급 성공 응답 수)를 먼저 넣는다.

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
) checks;
