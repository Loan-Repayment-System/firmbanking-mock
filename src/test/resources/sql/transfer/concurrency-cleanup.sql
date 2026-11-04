DELETE FROM transfer WHERE company_no = 'CONCUR00001';
DELETE FROM company WHERE company_no = 'CONCUR00001';
DELETE FROM account WHERE bank_code = '999' AND account_no LIKE '900-000-%';
