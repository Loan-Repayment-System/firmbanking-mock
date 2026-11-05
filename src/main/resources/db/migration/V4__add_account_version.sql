-- 낙관적 락. 잔액을 바꿀 때 조회한 version과 같을 때만 바꾸고 1 올린다.
ALTER TABLE account
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER balance;
