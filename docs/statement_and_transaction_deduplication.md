# Specification: Statement & Transaction Deduplication

## 1. Goal & Objectives
Prevent duplicate statements and transactions from being imported into the application:
1. **Accidental Full Statement Re-upload:** If a user re-uploads the same statement (even if renamed or downloaded again), the application must identify that 100% of transactions already exist, reject the import, and not create an empty "ghost" statement in the database.
2. **Overlapping Statement Periods:** If a user uploads custom-period statements (e.g. Aug 1 - Sep 5, then Sep 1 - Sep 30), transactions in the overlapping period (Sep 1 - Sep 5) must be identified as duplicates, skipped, and reported with references to the existing statements that already own them.
3. **Cross-Instrument Upload Warning:** If a user accidentally uploads a statement file under the wrong instrument (e.g. uploading Account A's statement under Account B), the system flags the collision against Account A and aborts.

---

## 2. Core Deduplication Logic

### A. The Duplicate Key (Fingerprint)
A transaction's uniqueness is determined exclusively by its **parsed raw values** (derived fields such as category or user review status are ignored).

Transactions are matched using a **Two-Tier Key**:

1. **Tier 1 (When `bankReference` is present):**
   - Fields: `datetime` + `amount` + `direction` + `bankReference`
   - Applicable for: UPI, IMPS, NEFT, Cheques where banks issue a reference number (UTR/RRN).
   - `bankReference` paired with `datetime` guarantees global uniqueness while preventing any reference collisions across years.

2. **Tier 2 (Fallback when `bankReference` is null/blank):**
   - Fields: `datetime` + `amount` + `direction` + `normalizedCounterparty`
   - Applicable for: Credit card swipes, POS transactions, ATM cash withdrawals.
   - `normalizedCounterparty`: Trimmed, collapsed spaces, uppercase.

> [!NOTE]
> `userComment`, `mode`, and `rawTxn` are excluded to keep matching robust against minor formatting variations between bank statement exports.

---

### B. Multiset / Frequency Matching (Handling Identical Transactions)
If a user legitimately makes two identical transactions on the same day (e.g. two ₹250 coffee purchases at Starbucks without a reference number):
- **Scenario:** Database already contains 1 occurrence. Incoming statement contains 2 occurrences.
- **Algorithm:**
  1. Group existing DB transactions in the date range into queues indexed by their `DuplicateKey`: `Map<DuplicateKey, Queue<NormalizedTransaction>>`.
  2. For each incoming transaction:
     - Check if `queue` for its key has an available match.
     - **If Match Found:** Poll/consume one match from the queue. Mark the incoming transaction as **Duplicate**.
     - **If No Match Found (or Queue Empty):** The incoming transaction is **New**.
- This ensures 1-to-1 matching without falsely marking distinct legitimate purchases as duplicates.

---

### C. Date Range Scope (No Buffer)
- When fetching existing transactions from the database for comparison, use the exact incoming date range:
  `WHERE datetime >= :minIncomingDate AND datetime <= :maxIncomingDate`
- **Zero date buffer:** Bank statements record exact transaction dates. Adding arbitrary buffers is avoided to prevent false matches with recurring daily transactions.

---

### D. Cross-Instrument Collision Detection
- Check whether incoming transactions match existing transactions under a **different** `instrument_id`:
  - If 100% (or majority) of incoming transactions match existing transactions belonging to another instrument:
  - Abort the upload and alert the user:
    ```text
    ⚠️ Cross-Instrument Collision Detected!
    All 45 transactions in this file already exist under instrument 'HDFC_SALARY'.
    Did you select the wrong instrument? Import aborted.
    ```

---

## 3. Storage & Workflow Design

### A. Execution Pipeline: "Parse → Deduplicate → Decide → Persist"
Decouple parsing from persistence so that rejected statements never create database records:

```text
Step 1: Parse File in Memory
   Input: File, StatementType
   Output: List<NormalizedTransaction> (statementId is null, 0 DB writes)

Step 2: Deduplicate (TransactionDeduplicationEngine)
   Input: instrumentId, List<NormalizedTransaction>
   Query DB: Existing transactions in [minDate, maxDate]
   Output: DeduplicationResult {
       List<NormalizedTransaction> newTransactions,
       List<DuplicateMatch> duplicates,
       Optional<String> crossInstrumentWarning
   }

Step 3: Decide & Persist
   Case 1: Cross-Instrument Warning
      -> Abort immediately. 0 DB writes.
   Case 2: 100% Duplicate (newTransactions is empty)
      -> Do NOT create Statement. 0 DB writes.
      -> Render duplicate breakdown table.
   Case 3: Partial or 0% Duplicate (newTransactions > 0)
      -> statementRepository.saveStatement(...)
      -> Set new statementId on newTransactions
      -> transactionMapper.enrich(newTransactions)
      -> transactionRepository.saveTransactions(newTransactions)
      -> Render import summary + duplicate breakdown table (if any duplicates skipped).
```

---

## 4. Domain & Model Classes (Package: `io.github.harshjoeyit.core.dedup`)

### A. `DuplicateMatch`
```java
public record DuplicateMatch(
    NormalizedTransaction incoming,   // Skipped transaction
    NormalizedTransaction existing    // Matching transaction already in DB
) {}
```

### B. `DuplicateKey`
```java
public record DuplicateKey(
    LocalDate date,
    Double amount,
    TxnDirection direction,
    String identifier // bankReference (Tier 1) or normalized counterparty (Tier 2)
) {
    public static DuplicateKey from(NormalizedTransaction txn) {
        LocalDate date = txn.getDatetime().toLocalDate();
        String id = (txn.getBankReference() != null && !txn.getBankReference().isBlank())
                ? txn.getBankReference().trim().toUpperCase()
                : normalize(txn.getCounterparty());
        return new DuplicateKey(date, txn.getAmount(), txn.getDirection(), id);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toUpperCase();
    }
}
```

### C. `StatementOverlapSummary`
```java
public record StatementOverlapSummary(
    UUID statementId,
    String instrumentId,
    int duplicateCount,
    LocalDate minDate,
    LocalDate maxDate
) {}
```

### D. `DeduplicationResult`
```java
public record DeduplicationResult(
    List<NormalizedTransaction> newTransactions,
    List<DuplicateMatch> duplicates,
    List<StatementOverlapSummary> statementSummaries,
    String crossInstrumentCollision
) {
    public boolean isFullDuplicate() {
        return newTransactions.isEmpty() && !duplicates.isEmpty();
    }
    
    public boolean hasCrossInstrumentCollision() {
        return crossInstrumentCollision != null;
    }
}
```

### E. Repository Query
In `TransactionRepository`:
```java
List<NormalizedTransaction> findByDateRange(LocalDateTime start, LocalDateTime end);
```
SQL:
```sql
SELECT * FROM transactions 
WHERE datetime >= :start AND datetime <= :end 
ORDER BY datetime ASC
```

### F. Engine Interface
```java
public interface DeduplicationEngine {
    DeduplicationResult deduplicate(String targetInstrumentId, List<NormalizedTransaction> incomingTxns);
}
```

---

## 5. CLI User Experience

### Scenario 1: Partial Duplicates (Overlapping Periods)
```text
Statement parsed: 45 transactions found.

⚠️  Duplicate Transactions: 28 skipped (already recorded)
┌──────────────────────────────────────┬─────────────┬───────────┬─────────────────────────┐
│ Existing Statement ID                │ Instrument  │ Duplicates│ Overlapping Period      │
├──────────────────────────────────────┼─────────────┼───────────┼─────────────────────────┤
│ 3f4a21... (hdfc_aug_part1.csv)       │ HDFC_SALARY │ 10        │ 10-Aug-2024 - 15-Aug-2024│
│ 8c1d59... (hdfc_aug_part2.csv)       │ HDFC_SALARY │ 18        │ 16-Aug-2024 - 31-Aug-2024│
└──────────────────────────────────────┴─────────────┴───────────┴─────────────────────────┘

✓ Saved Statement [d92f...]: 17 new transactions imported (01-Sep-2024 to 10-Sep-2024).
Run 'review' to start reviewing new transactions.
```

### Scenario 2: 100% Duplicate Statement
```text
Statement parsed: 35 transactions found.

⚠️  Duplicate Statement: All 35 transactions have already been imported!
┌──────────────────────────────────────┬─────────────┬───────────┬─────────────────────────┐
│ Existing Statement ID                │ Instrument  │ Duplicates│ Date Range              │
├──────────────────────────────────────┼─────────────┼───────────┼─────────────────────────┤
│ 3f4a21... (hdfc_aug.csv)             │ HDFC_SALARY │ 35        │ 01-Aug-2024 - 31-Aug-2024│
└──────────────────────────────────────┴─────────────┴───────────┴─────────────────────────┘

Statement was not saved. No new transactions to import.
```

### Scenario 3: Cross-Instrument Warning
```text
⚠️  Cross-Instrument Collision Detected!
All 35 transactions in this file already exist under instrument 'HDFC_SALARY'.
Selected instrument: 'HDFC_SAVINGS'
Statement was not saved. Please verify the instrument.
```

---

## 6. Cascade Delete Note
- Transactions belong to the `Statement` in which they were first imported.
- If a statement is deleted in the database, its associated transactions are deleted via foreign key `ON DELETE CASCADE`.
- Skipping duplicate transactions in later statements preserves this single source of truth without duplicating rows or creating split ownership.
