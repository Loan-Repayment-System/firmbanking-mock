INSERT INTO account (bank_code, account_no, holder_name, holder_birth_date, balance) VALUES
    ('999', '900-000-000001', '동시성', NULL, 0),
    ('999', '900-000-000002', '동시성', NULL, 0),
    ('999', '900-000-000003', '고객', '900101', 100000);

INSERT INTO company (company_no, company_name, collection_account_id, payment_account_id)
SELECT 'CONCUR00001', '동시성', collection.account_id, payment.account_id
FROM account collection
    JOIN account payment
        ON payment.bank_code = '999' AND payment.account_no = '900-000-000002'
WHERE collection.bank_code = '999'
  AND collection.account_no = '900-000-000001';

INSERT INTO withdrawal_consent (company_no, account_id, consent_status_id, payer_no, applied_date)
SELECT 'CONCUR00001', account_id, 1, 'CONCUR', '20261104'
FROM account
WHERE bank_code = '999'
  AND account_no = '900-000-000003';
