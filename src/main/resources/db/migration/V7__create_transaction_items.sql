-- Create transaction_items table to store itemized breakdown of transactions
-- (e.g. Swiggy Food dishes, Amazon products, Flipkart items)
CREATE TABLE IF NOT EXISTS transaction_items (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id    TEXT NOT NULL,
    item_name         TEXT NOT NULL,
    quantity          INTEGER NOT NULL DEFAULT 1,
    unit_price        REAL,
    total_price       REAL NOT NULL,
    outlet_name       TEXT,
    platform          TEXT,
    platform_order_id TEXT,
    created_at        TEXT NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_txn_items_txn_id ON transaction_items(transaction_id);
