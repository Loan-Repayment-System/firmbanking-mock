-- ============================================================
-- V1: 테이블
-- 기준(bank, company) · 상태(account, withdrawal_consent) · 기록(transfer) · 코드(transfer_type, consent_status)
-- ============================================================

CREATE TABLE bank (
    bank_code  CHAR(3)     NOT NULL,
    bank_name  VARCHAR(50) NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (bank_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- holder_birth_date는 업체 계좌에 없으므로 NULL을 허용한다.
CREATE TABLE account (
    account_id        BIGINT        NOT NULL AUTO_INCREMENT,
    bank_code         CHAR(3)       NOT NULL,
    account_no        VARCHAR(20)   NOT NULL,
    holder_name       VARCHAR(50)   NOT NULL,
    holder_birth_date CHAR(6)       NULL,
    balance           DECIMAL(13,0) NOT NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id),
    UNIQUE KEY uq_account_bank_code_account_no (bank_code, account_no),
    CONSTRAINT fk_account_bank
        FOREIGN KEY (bank_code) REFERENCES bank (bank_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE company (
    company_no            CHAR(12)    NOT NULL,
    company_name          VARCHAR(50) NOT NULL,
    collection_account_id BIGINT      NOT NULL,
    payment_account_id    BIGINT      NOT NULL,
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (company_no),
    CONSTRAINT fk_company_collection_account
        FOREIGN KEY (collection_account_id) REFERENCES account (account_id),
    CONSTRAINT fk_company_payment_account
        FOREIGN KEY (payment_account_id) REFERENCES account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transfer_type (
    transfer_type_id TINYINT     NOT NULL,
    name             VARCHAR(20) NOT NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transfer_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE consent_status (
    consent_status_id TINYINT     NOT NULL,
    name              VARCHAR(20) NOT NULL,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consent_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 업체·계좌당 한 행. 해지 후 다시 등록하면 같은 행의 상태를 바꾼다.
CREATE TABLE withdrawal_consent (
    consent_id        BIGINT      NOT NULL AUTO_INCREMENT,
    company_no        CHAR(12)    NOT NULL,
    account_id        BIGINT      NOT NULL,
    consent_status_id TINYINT     NOT NULL,
    payer_no          VARCHAR(20) NOT NULL,
    applied_date      CHAR(8)     NOT NULL,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (consent_id),
    UNIQUE KEY uq_withdrawal_consent_company_no_account_id (company_no, account_id),
    CONSTRAINT fk_withdrawal_consent_company
        FOREIGN KEY (company_no) REFERENCES company (company_no),
    CONSTRAINT fk_withdrawal_consent_account
        FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_withdrawal_consent_consent_status
        FOREIGN KEY (consent_status_id) REFERENCES consent_status (consent_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 지급·집금 전문 한 건당 한 행. INSERT만 한다.
-- 계좌는 FK 대신 요청 값 그대로 둔다. 계좌가 없는 거절(B001·B002)도 남겨야 하기 때문이다.
CREATE TABLE transfer (
    transfer_id           BIGINT        NOT NULL AUTO_INCREMENT,
    company_no            CHAR(12)      NOT NULL,
    sent_date             CHAR(8)       NOT NULL,
    message_no            CHAR(6)       NOT NULL,
    sent_time             CHAR(6)       NOT NULL,
    transfer_type_id      TINYINT       NOT NULL,
    withdrawal_account_no VARCHAR(20)   NOT NULL,
    deposit_bank_code     CHAR(3)       NOT NULL,
    deposit_account_no    VARCHAR(20)   NOT NULL,
    amount                DECIMAL(13,0) NOT NULL,
    memo                  VARCHAR(14)   NOT NULL,
    response_code         CHAR(4)       NOT NULL,
    balance_after         DECIMAL(13,0) NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transfer_id),
    -- 거래고유번호. 같은 전문이 동시에 들어와도 한 건만 저장된다.
    UNIQUE KEY uq_transfer_company_no_sent_date_message_no (company_no, sent_date, message_no),
    CONSTRAINT fk_transfer_company
        FOREIGN KEY (company_no) REFERENCES company (company_no),
    CONSTRAINT fk_transfer_transfer_type
        FOREIGN KEY (transfer_type_id) REFERENCES transfer_type (transfer_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
