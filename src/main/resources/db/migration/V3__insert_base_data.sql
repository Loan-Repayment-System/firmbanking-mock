-- ============================================================
-- V3: 기준 데이터. repayment-backend 설정값과 같다.
-- ============================================================

INSERT INTO bank (bank_code, bank_name) VALUES
    ('999', 'Mock 은행');

INSERT INTO account (bank_code, account_no, holder_name, holder_birth_date, balance) VALUES
    ('999', '100-000-000001', '캐피탈', NULL, 0),
    ('999', '100-000-000002', '캐피탈', NULL, 10000000000);

INSERT INTO company (company_no, company_name, collection_account_id, payment_account_id)
SELECT 'CAPITAL00001', '캐피탈', collection.account_id, payment.account_id
FROM account collection
    JOIN account payment
        ON payment.bank_code = '999' AND payment.account_no = '100-000-000002'
WHERE collection.bank_code = '999'
  AND collection.account_no = '100-000-000001';
