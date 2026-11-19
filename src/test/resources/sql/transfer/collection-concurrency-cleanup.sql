DELETE FROM transfer WHERE company_no = 'CONCUR00001';
DELETE FROM account_transaction
WHERE account_id IN (SELECT account_id FROM account WHERE bank_code = '999' AND account_no LIKE '900-000-%');
DELETE FROM transfer_request WHERE company_no = 'CONCUR00001';
DELETE FROM withdrawal_consent WHERE company_no = 'CONCUR00001';
DELETE FROM company WHERE company_no = 'CONCUR00001';
DELETE FROM account WHERE bank_code = '999' AND account_no LIKE '900-000-%';
