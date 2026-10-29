-- ============================================================
-- V2: 코드 데이터. id는 enum과 1:1이다(TransferType, ConsentStatus).
-- ============================================================

INSERT INTO transfer_type (transfer_type_id, name) VALUES
    (1, '지급'),
    (2, '집금');

INSERT INTO consent_status (consent_status_id, name) VALUES
    (1, '등록'),
    (2, '해지');
