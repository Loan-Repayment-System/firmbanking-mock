INSERT INTO account (bank_code, account_no, holder_name, holder_birth_date, balance) VALUES
    ('999', '900-000-000001', '동시성', NULL, 0),
    ('999', '900-000-000002', '동시성', NULL, 100000000),
    ('999', '900-000-000003', '고객', '900101', 0);

INSERT INTO company (company_no, company_name, collection_account_id, payment_account_id)
SELECT 'CONCUR00001', '동시성', collection.account_id, payment.account_id
FROM account collection
    JOIN account payment
        ON payment.bank_code = '999' AND payment.account_no = '900-000-000002'
WHERE collection.bank_code = '999'
  AND collection.account_no = '900-000-000001';
