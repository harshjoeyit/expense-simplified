# ExpenseSimplified: Pre-Flight Application Audit & Edge-Case Report

**Author:** Antigravity Pairing Assistant  
**Date:** November 2026  
**Scope:** Full application end-to-end audit (Parsing, Deduplication, Review Workflow, Friend & Split Management, Rule Mapping, SQLite Persistence, and CSV Export).

---

## Executive Summary

Before importing real-world statements to categorize and split transactions, we conducted an exhaustive static analysis and scenario simulation across all components.

While the core architecture (two-tier deduplication, interactive JLine review navigation, SQLite schema, and `myShare` export) is well-designed and covered by 200 passing unit tests, **there are 3 critical failure risks** and several usability friction points that you need to be aware of before loading production statements.

Most notably, **importing an HDFC Account statement with unmapped transactions will crash on SQLite** due to a database constraint mismatch (`NOT NULL` constraint on `transactions.type`).

---

## 1. Showstoppers & Critical Risks

### 🚨 1. SQLite `NOT NULL` Crash on `transactions.type` for Unreviewed Account Transactions
- **Location:** `V1__create_schema.sql` (Line 22) vs `HdfcAccountStatementParser.java` (Lines 93–110)
- **The Issue:**
  - In `HdfcAccountStatementParser`, `txn.getType()` is intentionally left unset (`null`) because an account debit/credit is not inherently known to be a `PURCHASE`, `SALARY`, `INVESTMENT`, or `TRANSFER` until manual review or rule matching occurs.
  - If a transaction does not match an existing rule in `TransactionMapper`, it arrives at `JdbcTransactionRepository.saveTransactions()` with `type = null`.
  - In `V1__create_schema.sql`, the column is defined as `type TEXT NOT NULL`.
  - SQLite will throw:
    ```text
    org.sqlite.SQLiteException: [SQLITE_CONSTRAINT_NOTNULL] NOT NULL constraint failed: transactions.type
    ```
  - **Impact:** Any HDFC account statement with new/unmapped transactions will completely fail to import and throw a database exception.
  - **Workaround / Fix:** A Flyway migration is needed to make `type` nullable in the `transactions` table (just as `V3` did for `mapping_rules`), or assign a fallback `null`-safe handling before DB insert.

---

### 🚨 2. Floating-Point Equality Trap in Custom & Percent Splits
- **Location:** `SplitService.java` (Lines 289, 301)
- **The Issue:**
  - In `validatePercentSplit`:
    ```java
    Double percentSum = splitInput.valuePerFriend().values().stream().mapToDouble(Double::doubleValue).sum();
    if (!percentSum.equals(100.0)) {
        throw new InvalidSplitException("Aggregated percent is not 100%");
    }
    ```
  - In `validateCustomSplit`:
    ```java
    Double sum = splitInput.valuePerFriend().values().stream().mapToDouble(Double::doubleValue).sum();
    if (!sum.equals(splitInput.amount())) {
        throw new InvalidSplitException("Total amount does not match aggregated split amount");
    }
    ```
  - In Java, floating-point `double` arithmetic suffers from binary representation error (e.g. `33.33 + 33.33 + 33.34 = 100.00000000000001` or `0.1 + 0.2 != 0.3`).
  - Using `!sum.equals(target)` tests exact bitwise identity instead of tolerance (`Math.abs(sum - target) < 0.01` or `BigDecimal`).
  - **Impact:** Splitting transactions using `PERCENT` or `CUSTOM` with fractional amounts (like 3-way 33.33% splits or cents/paise) will falsely throw `InvalidSplitException` and prevent the user from saving the split.

---

### 🚨 3. Silent Skipping of Non-UPI / Non-IMPS Transactions in HDFC Account
- **Location:** `HdfcAccountStatementParser.java` (Lines 113–119, 138–143)
- **The Issue:**
  - `HdfcAccountStatementParser` delegates narration parsing to Spring-injected `NarrationParser` implementations.
  - Currently, **only two narration parsers exist**: `UpiNarrationParser` (starts with `UPI-`) and `ImpsNarrationParser` (starts with `IMPS-`).
  - If your statement has:
    - POS Debit Card purchases (`POS 4012...`)
    - ATM cash withdrawals (`NWD-...`, `ATW-...`)
    - NEFT or RTGS transfers
    - Salary credits (`ACH-...`, `SALARY-...`)
    - Bank charges, SMS fees, interest credits (`INT.PD...`, `CHG...`)
  - `getNarrationParser()` throws `UnsupportedNarrationException`, which is caught in the loop:
    ```java
    catch (UnsupportedNarrationException | MalformedNarrationException e) {
        log.warn("Skipping record [line {}] due to narration issue...", record.getRecordNumber());
    }
    ```
  - **Impact:** Those transactions are **silently omitted** from the import. If your statement has 50 rows and 15 are debit card swipes, only 35 UPI/IMPS transactions will be imported.

---

## 2. Parsing Fragilities & Format Sensitivities

| Component | Strict Assumption | Real-World Failure Scenario | Severity |
| :--- | :--- | :--- | :--- |
| **HDFC Account Headers** | Expects exact column `"Value Dat"` | In some HDFC Netbanking exports, the header is `"Value Date"`. If `"Value Date"` is present, `actualHeaders.equals(EXPECTED_STMT_HEADER)` throws `MalformedCsvException`. | Medium |
| **HDFC Account Dates** | Expects pattern `dd/MM/yy` (2-digit year) | If your bank statement outputs 4-digit years `dd/MM/yyyy` (e.g. `01/08/2026`), `LocalDate.parse()` throws `DateTimeParseException` and skips the row. | High |
| **HDFC CC Delimiter** | Expects `~|~` (tilde-pipe-tilde) | If you open the CSV in Microsoft Excel or Google Sheets and save it as a standard CSV (comma-separated), the parser fails to find the header prefix and aborts. Always supply the raw downloaded file. | Medium |
| **HDFC CC Loop Termination** | Breaks when `Transaction type` is not "Domestic" or "International" | If HDFC includes an interim line (e.g. EMI installment note, milestone reward note, or trailing delimiter row), line 115 executes `break;`, which stops parsing and drops all subsequent transactions in the file. | High |
| **Scapia CC Headers** | Expects exact lowercase: `date`, `time`, `description`, `amount` | If the Federal Bank / Scapia download contains Title Case (`Date`, `Time`, `Description`, `Amount`), `equals(EXPECTED_STMT_HEADER)` fails with `MalformedCsvException`. | Medium |
| **Scapia CC Date Format** | Expects `dd-MM-yyyy` (hyphens) | If the file uses `dd/MM/yyyy` (slashes), `LocalDate.parse` fails and records are skipped. | Medium |
| **UPI IFSC Requirement** | Requires 11-character bank IFSC code in UPI narration | Merchant QR transactions or newer bank narration formats that omit the IFSC code will fail `narrationTooling.extractBankIfsc()` and get skipped. | High |

---

## 3. Workflow & UX Limitations

### 1. No "Unsplit" Option
- If you split a transaction by mistake and want to revert it back to normal (unsplit), there is currently no CLI mechanism to remove splits.
- In `promptSplit`, selecting only "Myself" produces: `⚠ Need to select at least one friend (other than self) to split`.
- **Workaround:** You can only re-split it with someone else; to completely remove the split, rows must be deleted from SQLite table `transaction_splits` and `my_share` reset to `amount`.

### 2. Rule Saving Does Not Retroactively Apply to Current Statement
- Pressing `[r]` in the review loop saves the mapping rule into SQLite for future imports.
- It does **not** categorize the current transaction (you must still press `[c]`), nor does it retroactively categorize other matching transactions already loaded into the review session.

### 3. CLI Command Typo in Friend Hint
- In `StatementCommands.java:414`, the prompt displays:
  `⚠ No friends configured. Add friends using 'friend add' command`
- The actual Spring Shell command is `friend-add` (hyphenated). Typing `friend add` returns a syntax error.

### 4. Unknown Instrument Error Message
- If you pass an instrument identifier not in `instruments.yml` (e.g. typing `scapia` instead of `scapia-cc`), the CLI reports:
  `Error: statement type could not be determined`
  without listing the available configured instruments (`hdfc-sal-acc`, `hdfc-cc-mil`, `scapia-cc`).

### 5. Export Only Includes Reviewed Transactions
- If you import 40 transactions, review 10, and run `export -s <id>`, the output CSV will contain **only the 10 reviewed transactions**.
- `PENDING` transactions are intentionally excluded from CSV export. If you expect all transactions in the export, you must review them first.

---

## 4. Summary Matrix: Where the App Succeeds vs Falls Short

```mermaid
flowchart TD
    subgraph Succeeded["✅ What Works Exceptionally Well"]
        A["Deduplication Engine (0 Ghost Statements, Multiset matching)"]
        B["Fast Review Loop (Raw keystrokes n, p, c, s, r, q)"]
        C["myShare & Splitwise Debt Tracking"]
        D["SQLite Immutability on Provenance Fields"]
        E["Clean CSV Export with Dynamic Paths & Filenames"]
    end

    subgraph Attention["⚠️ Needs Attention Before Real Use"]
        F["HDFC Account: transactions.type NOT NULL DB Constraint"]
        G["HDFC Account: Skips all non-UPI/IMPS (Debit Card/ATM/NEFT)"]
        H["SplitService: Double.equals floating point comparison"]
        I["Strict Parser Headers & Delimiters"]
        J["No 'Unsplit' command in Review Loop"]
    end
```

---

## 5. Recommended Action Plan

Before running your full statements:

1. **Fix `transactions.type` SQLite constraint (Immediate):**
   Add a Flyway migration `V6__allow_null_transaction_type.sql` to make `type` nullable in `transactions`, allowing unreviewed account transactions to be saved.
2. **Add a Fallback Narration Parser for HDFC Account (Immediate):**
   Add a generic fallback parser (or update `HdfcAccountStatementParser`) so that debit card purchases, ATM withdrawals, and bank fees are captured with `mode = DEBIT_CARD / OTHER` and `counterparty = narration`, instead of being dropped.
3. **Change Double equality to delta in `SplitService` (Immediate):**
   Change `!percentSum.equals(100.0)` to `Math.abs(percentSum - 100.0) > 0.01` and similarly for `sum.equals(amount)`.
4. **Configure Friends first:**
   Run `friend-add -n <FriendName>` before launching `review`, so you can immediately split transactions without exiting the review loop.
