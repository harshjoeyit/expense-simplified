-- Transactions: Make 'type' nullable.
-- Why: Transactions parsed from bank account statements (such as HDFC UPI / IMPS)
-- do not have an inherently predetermined transaction type (PURCHASE, SALARY, INVESTMENT,
-- LEND, etc.) at parse time. 'type' is deliberately kept NULL in the domain model
-- until assigned via merchant mapping rules or user categorization during review.

CREATE TABLE IF NOT EXISTS transactions_new (
    id                TEXT PRIMARY KEY,
    instrument_id     TEXT NOT NULL,
    statement_id      TEXT NOT NULL,
    datetime          TEXT NOT NULL,
    value_datetime    TEXT,
    amount            REAL NOT NULL,
    my_share          REAL,
    direction         TEXT NOT NULL,
    type              TEXT, -- NULLABLE: Pending categorization by user review or rule
    mode              TEXT,
    category          TEXT, -- NULLABLE: Pending categorization by user review or rule
    counterparty      TEXT,
    counterparty_id   TEXT,
    counterparty_type TEXT,
    user_comment      TEXT,
    bank_reference    TEXT,
    review_status     TEXT NOT NULL,
    review_remarks    TEXT,
    raw_txn           TEXT NOT NULL,
    created_at        TEXT NOT NULL,
    updated_at        TEXT NOT NULL,
    FOREIGN KEY (statement_id) REFERENCES statements(id) ON DELETE CASCADE
);

INSERT INTO transactions_new (
    id, instrument_id, statement_id, datetime, value_datetime,
    amount, my_share, direction, type, mode,
    category, counterparty, counterparty_id, counterparty_type,
    user_comment, bank_reference, review_status, review_remarks,
    raw_txn, created_at, updated_at
)
SELECT
    id, instrument_id, statement_id, datetime, value_datetime,
    amount, my_share, direction, type, mode,
    category, counterparty, counterparty_id, counterparty_type,
    user_comment, bank_reference, review_status, review_remarks,
    raw_txn, created_at, updated_at
FROM transactions;

DROP TABLE transactions;

ALTER TABLE transactions_new RENAME TO transactions;

CREATE INDEX IF NOT EXISTS idx_transactions_statement_id ON transactions(statement_id);
CREATE INDEX IF NOT EXISTS idx_transactions_review_status ON transactions(review_status);
CREATE INDEX IF NOT EXISTS idx_transactions_datetime ON transactions(datetime);
