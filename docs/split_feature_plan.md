# Implementation Plan - Expense Splitting Feature

## 1. Overview
Build an end-to-end expense splitting feature that allows users to split expenses with configured friends during statement review or via commands, calculates shares (Equal or Exact using the Auto-Remainder pattern), tracks individual debts, updates `myShare` on the transaction for downstream accounting, and manages Splitwise posting status (`PENDING` / `POSTED`).

---

## 2. Architecture & Component Interaction

```text
friends.yml (Config)
      │
      ▼
FriendConfig (Spring ConfigurationProperties)
      │
      ▼
SplitService ◄──► SplitRepository (InMemorySplitRepository)
      │                     ▲
      ▼                     │
StatementCommands (CLI) ────┘
  ├── 1. Interactive Review prompt [s] (Split key)
  │      ├── Select participants (You/myself + Friends)
  │      ├── Select strategy (EQUAL / EXACT)
  │      └── Enter shares (Auto-Remainder calculation)
  └── 2. 'splits' command
         ├── List pending splits to enter into Splitwise
         └── Mark splits as posted
```

---

## 3. Data & Domain Models

### A. Configuration: `friends.yml` & `FriendConfig`
- File: `src/main/resources/friends.yml` (and `friends.example.yml` in git, `.gitignore` updated).
- Model:
  ```yaml
  friends:
    - id: "alice"
      name: "Alice Smith"
      splitwise_id: "12345"    # Optional, for Phase 2 API
    - id: "bob"
      name: "Bob Jones"
  ```
- Java Record:
  ```java
  public record Friend(String id, String name, String splitwiseId) {}
  
  @ConfigurationProperties
  public record FriendConfig(List<Friend> friends) {}
  ```

### B. Transaction Update: `NormalizedTransaction`
- Add field: `private Double myShare;`
- Getter logic:
  - If `myShare == null` $\to$ returns original `amount`.
  - If transaction is split $\to$ returns user's share.
  - If user paid 100% on behalf of others $\to$ `myShare = 0.0`.

### C. Split Model: `TransactionSplit`
- Fields:
  - `UUID id`: Unique ID of the split record.
  - `UUID transactionId`: Reference to the parent `NormalizedTransaction`.
  - `String friendId`: ID/key of the friend.
  - `String friendName`: Display name at the time of split.
  - `double amount`: The friend's share.
  - `SplitStrategy strategy`: `EQUAL` or `EXACT`.
  - `SplitStatus status`: `PENDING` or `POSTED`.
  - `LocalDateTime createdAt`: Timestamp.
- Enums:
  - `SplitStrategy`: `EQUAL`, `EXACT`.
  - `SplitStatus`: `PENDING`, `POSTED`.

---

## 4. Business Logic & Calculation (Auto-Remainder Pattern)

Given total transaction amount $T$:
1. **Equal Split:**
   - Participants count $N$ (including user if `includeSelf = true`).
   - Base share: $\text{share} = \text{roundDown2Decimals}(T / N)$.
   - Remainder: $R = T - (\text{share} \times N)$.
   - Each friend gets `share`.
   - If `includeSelf = true`, user gets $\text{share} + R$.
   - If `includeSelf = false`, the first friend gets $\text{share} + R$.
2. **Exact / Custom Split (Auto-Remainder):**
   - For each friend $1 \dots K-1$: prompt user for amount $A_i$.
   - Track remaining balance $T_{\text{rem}} = T - \sum A_i$.
   - If `includeSelf = true`:
     - User automatically receives remaining balance $T_{\text{rem}}$ as `myShare`.
   - If `includeSelf = false`:
     - Last friend automatically defaults to $T_{\text{rem}}$ (user presses Enter to accept).

---

## 5. Review Flow & Keystroke Integration

### Key Binding Update in `StatementCommands`
- Current: `[Enter/c] Categorize  [→/s] Next  [←/p] Prev  [q/Esc] Quit`
- Updated:
  ```text
  [Enter/c] Categorize  [s] Split  [→/n] Next  [←/p] Prev  [q/Esc] Quit
  ```
- Rebind `NEXT` to `[→]`, `[n]`, `[N]`.
- Bind `SPLIT` to `[s]`, `[S]`.

### Split Prompt UX on `[s]`
1. **Eligibility Check:**
   - Check if `txn.getType()` is `INCOME` or if rule has `canSplit == false` $\to$ display warning and skip.
2. **Categorize first if needed:**
   - If transaction is unreviewed (`PENDING`), prompt for `TxnType` (default `PURCHASE`) and `TxnCategory`.
3. **Friend Selection:**
   - Checkbox / multi-select with items:
     - `[x] You (myself)`
     - Configured friends from `FriendConfig`.
4. **Strategy Selection:**
   - Select: `1. Equal split` or `2. Exact amounts`.
5. **Amount Input (if Exact):**
   - Prompt with running remaining balance.
6. **Execution & Confirmation:**
   - Calls `splitService.createSplits(...)`.
   - Sets `txn.setMyShare(...)` and marks transaction resolved/reviewed.
   - Prints: `✓ Split saved: Your share = ₹500.00 | Alice owes = ₹300.00 | Bob owes = ₹200.00`.

---

## 6. The `splits` CLI Command

Add `@Command(command = "splits")` in `StatementCommands` (or `SplitCommands`):
```bash
splits                    # List up to 10 pending splits
splits -n 25              # Custom limit
splits -s <statement-id>  # Splits for a specific statement
splits --mark-posted <id> # Mark a split as POSTED to Splitwise
```

Table Output Columns:
`Date | Transaction Description | Friend | Amount (₹) | Strategy | Status | Split ID`

---

## 7. Step-by-Step Implementation Roadmap (Loop: Guide ➔ Implement ➔ Review)

- [ ] **Phase 1: Configuration & Domain Models**
  - Create `friends.example.yml` and add `friends.yml` to `.gitignore` and `application.yml`.
  - Create `FriendConfig` and `Friend` model.
  - Update `NormalizedTransaction` with `myShare`.
  - Create `TransactionSplit`, `SplitStrategy`, and `SplitStatus`.
- [ ] **Phase 2: Repository & Split Service**
  - Create `SplitRepository` and `InMemorySplitRepository`.
  - Create `SplitService` with split calculation logic (Equal and Auto-Remainder Exact).
  - Write unit tests in `SplitServiceTest`.
- [ ] **Phase 3: CLI Review Flow Integration**
  - Update `StatementCommands` keymap (`[s]` for Split, `[n]` for Next).
  - Implement interactive split prompts in review loop.
- [ ] **Phase 4: CLI `splits` Command**
  - Implement `splits` command with tabular display and `--mark-posted` option.
  - Test end-to-end review and splitting workflow.

---

## 8. Verification Plan
- **Unit Tests:** `SplitServiceTest` testing Equal split rounding, Exact split with auto-remainder, and user exclusion (`includeSelf = false`).
- **Integration Test:** Verify that reviewing a statement with `[s]` creates split records in `SplitRepository` and correctly updates `txn.getMyShare()`.
- **CLI Verification:** Run `review`, split a real transaction, and verify output in `splits`.
