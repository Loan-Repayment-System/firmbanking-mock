-- ============================================================
-- V4: 계좌별 거래내역과 이체 요청 처리 상태
-- 코드(account_transaction_type, transfer_request_status) · 기록(account_transaction) · 상태(transfer_request)
-- id는 enum과 1:1이다(AccountTransactionType, TransferRequestStatus).
-- ============================================================

CREATE TABLE account_transaction_type (
    account_transaction_type_id TINYINT     NOT NULL,
    name                        VARCHAR(20) NOT NULL,
    created_at                  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_transaction_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transfer_request_status (
    transfer_request_status_id TINYINT     NOT NULL,
    name                       VARCHAR(20) NOT NULL,
    created_at                 DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transfer_request_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO account_transaction_type (account_transaction_type_id, name) VALUES
    (1, '출금'),
    (2, '입금');

INSERT INTO transfer_request_status (transfer_request_status_id, name) VALUES
    (1, '처리 중'),
    (2, '완료'),
    (3, '오류');

-- 계좌의 출금·입금 한 건당 한 행. INSERT만 한다.
CREATE TABLE account_transaction (
    account_transaction_id      BIGINT        NOT NULL AUTO_INCREMENT,
    account_id                  BIGINT        NOT NULL,
    account_transaction_type_id TINYINT       NOT NULL,
    amount                      DECIMAL(13,0) NOT NULL,
    balance_after               DECIMAL(13,0) NOT NULL,
    created_at                  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_transaction_id),
    CONSTRAINT fk_account_transaction_account
        FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_account_transaction_account_transaction_type
        FOREIGN KEY (account_transaction_type_id) REFERENCES account_transaction_type (account_transaction_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 지급·집금 거래고유번호당 한 행. 처리 상태만 두고 처리 결과는 transfer에 둔다.
CREATE TABLE transfer_request (
    transfer_request_id        BIGINT   NOT NULL AUTO_INCREMENT,
    company_no                 CHAR(12) NOT NULL,
    sent_date                  CHAR(8)  NOT NULL,
    message_no                 CHAR(6)  NOT NULL,
    transfer_request_status_id TINYINT  NOT NULL,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (transfer_request_id),
    UNIQUE KEY uq_transfer_request_company_no_sent_date_message_no (company_no, sent_date, message_no),
    CONSTRAINT fk_transfer_request_company
        FOREIGN KEY (company_no) REFERENCES company (company_no),
    CONSTRAINT fk_transfer_request_transfer_request_status
        FOREIGN KEY (transfer_request_status_id) REFERENCES transfer_request_status (transfer_request_status_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 거절된 이체는 잔액이 움직이지 않아 거래가 없으므로 NULL이다.
ALTER TABLE transfer
    ADD COLUMN withdrawal_transaction_id BIGINT NULL,
    ADD COLUMN deposit_transaction_id    BIGINT NULL,
    ADD CONSTRAINT fk_transfer_withdrawal_transaction
        FOREIGN KEY (withdrawal_transaction_id) REFERENCES account_transaction (account_transaction_id),
    ADD CONSTRAINT fk_transfer_deposit_transaction
        FOREIGN KEY (deposit_transaction_id) REFERENCES account_transaction (account_transaction_id);
