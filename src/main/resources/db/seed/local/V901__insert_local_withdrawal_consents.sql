-- ============================================================
-- V901: 로컬 테스트용 출금 동의 (local 프로필만)
-- repayment-backend가 상환 계좌에서 집금할 수 있도록 캐피탈(CAPITAL00001) 출금 동의를 등록한다.
-- 납부자번호는 repayment-backend local 시드의 대출 계좌번호다.
-- 200-000-000003은 출금 동의 없음(C001) 확인용이라 넣지 않는다.
-- ============================================================

INSERT INTO withdrawal_consent (company_no, account_id, consent_status_id, payer_no, applied_date)
SELECT 'CAPITAL00001', a.account_id, 1, c.payer_no, DATE_FORMAT(CURDATE(), '%Y%m%d')
FROM account a
    JOIN (SELECT '200-000-000001' AS account_no, '900-000-000001' AS payer_no
          UNION ALL
          SELECT '200-000-000002', '900-000-000002') c
        ON c.account_no = a.account_no
WHERE a.bank_code = '999';
