CREATE TABLE IF NOT EXISTS friends (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT NOT NULL UNIQUE COLLATE NOCASE,
    splitwise_id INTEGER,
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL
);
