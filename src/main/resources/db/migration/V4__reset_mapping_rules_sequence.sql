-- Reset sqlite_sequence for mapping_rules to actual MAX(id) to fix sequence drift from past restarts
UPDATE sqlite_sequence
SET seq = COALESCE((SELECT MAX(id) FROM mapping_rules), 0)
WHERE name = 'mapping_rules';
