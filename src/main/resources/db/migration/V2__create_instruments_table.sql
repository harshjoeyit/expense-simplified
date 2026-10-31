-- 5. Instruments
CREATE TABLE IF NOT EXISTS instruments (
    id             TEXT PRIMARY KEY,
    description    TEXT NOT NULL,
    masked_num     TEXT,
    statement_type TEXT NOT NULL,
    user_id        TEXT,
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_instruments_user_id ON instruments(user_id);
