CREATE TABLE mapping_rules_new (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_type   TEXT NOT NULL,
    rule_key    TEXT NOT NULL,
    category    TEXT,
    type        TEXT,
    auto_review INTEGER NOT NULL,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    UNIQUE (rule_type, rule_key)
);

INSERT INTO mapping_rules_new (id, rule_type, rule_key, category, type, auto_review, created_at, updated_at)
SELECT id, rule_type, rule_key, category, type, auto_review, created_at, updated_at FROM mapping_rules;

DROP TABLE mapping_rules;

ALTER TABLE mapping_rules_new RENAME TO mapping_rules;
