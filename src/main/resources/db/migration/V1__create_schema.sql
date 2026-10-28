-- 1. Statements
CREATE TABLE IF NOT EXISTS statements (
    id            TEXT PRIMARY KEY,
    file_type     TEXT NOT NULL,
    file_path     TEXT NOT NULL,
    instrument_id TEXT NOT NULL,
    review_status TEXT NOT NULL,
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL
);

-- 2. Transactions
CREATE TABLE IF NOT EXISTS transactions (
    id                TEXT PRIMARY KEY,
    instrument_id     TEXT NOT NULL,
    statement_id      TEXT NOT NULL,
    datetime          TEXT NOT NULL,
    value_datetime    TEXT,
    amount            REAL NOT NULL,
    my_share          REAL,
    direction         TEXT NOT NULL,
    type              TEXT NOT NULL,
    mode              TEXT,
    category          TEXT,
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

CREATE INDEX IF NOT EXISTS idx_transactions_statement_id ON transactions(statement_id);
CREATE INDEX IF NOT EXISTS idx_transactions_review_status ON transactions(review_status);
CREATE INDEX IF NOT EXISTS idx_transactions_datetime ON transactions(datetime);

-- 3. Transaction Splits
CREATE TABLE IF NOT EXISTS transaction_splits (
    id             TEXT PRIMARY KEY,
    transaction_id TEXT NOT NULL,
    friend_id      TEXT NOT NULL,
    friend_name    TEXT NOT NULL,
    amount         REAL NOT NULL,
    strategy       TEXT NOT NULL,
    status         TEXT NOT NULL,
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_splits_txn_id ON transaction_splits(transaction_id);

-- 4. Mapping Rules
CREATE TABLE IF NOT EXISTS mapping_rules (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_type   TEXT NOT NULL,    -- 'COUNTERPARTY_ID' or 'COUNTERPARTY_NAME'
    rule_key    TEXT NOT NULL,    -- normalized counterparty ID or name
    category    TEXT NOT NULL,
    type        TEXT NOT NULL,
    auto_review INTEGER NOT NULL, -- 0 (false) or 1 (true)
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    UNIQUE (rule_type, rule_key)
);