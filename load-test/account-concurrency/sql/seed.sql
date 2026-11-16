-- 부하 측정용 고객 계좌. 앱이 Flyway로 기준 데이터(은행 999, 업체 CAPITAL00001, 수납·지급 계좌)를 넣은 뒤 run.sh가 실행한다.
-- 집금 고객 300-000-000001 ~ 300-000-500000: 잔액 1만, 출금 동의 등록. 고객 한 명은 한 번만 집금한다.
-- 지급 고객 400-000-000001 ~ 400-000-001000: 잔액 0. 성명조회도 이 계좌로 한다.

SET SESSION cte_max_recursion_depth = 500000;

INSERT INTO account (bank_code, account_no, holder_name, holder_birth_date, balance)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 500000
)
SELECT '999', CONCAT('300-000-', LPAD(n, 6, '0')), '집금고객', '900101', 10000
FROM seq;

INSERT INTO withdrawal_consent (company_no, account_id, consent_status_id, payer_no, applied_date)
SELECT 'CAPITAL00001', account_id, 1, account_no, '20261106'
FROM account
WHERE bank_code = '999'
  AND account_no LIKE '300-%';

INSERT INTO account (bank_code, account_no, holder_name, holder_birth_date, balance)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 1000
)
SELECT '999', CONCAT('400-000-', LPAD(n, 6, '0')), '지급고객', '900101', 0
FROM seq;
