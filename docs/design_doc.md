# Personal Finance Statement Parser — Architecture & Project Context

## 1. Project Goal

Build a **local Java-based personal finance statement parser and analytics application**.

Primary goal:

> Import bank and credit-card statements, normalize transactions into a bank-agnostic model, allow the user to review/validate ambiguous transactions, and eventually analyze spending.

Initial focus is **parsing and normalization**, not analytics.

Longer-term goals:
- Monthly/category-wise spending analysis.
- Comparisons across previous 6/12 months.
- Support multiple bank accounts and credit cards.
- Handle refunds/reversals.
- Avoid treating credit-card bill payments as expenses when the credit-card statement is also imported.
- Eventually load validated transactions into a local embedded database.
- Eventually provide a UI accessible on the local network.
- Notion integration may be used later for loading/exporting normalized data.

## 2. Core Preferences / Constraints

- Privacy is important because bank statements contain sensitive information.
- Avoid untrusted third-party services.
- Prefer local processing.
- Java is the primary implementation language.
- User prefers **guided implementation rather than receiving complete Java code immediately**.
- Explain the reasoning and architectural purpose before implementation where useful.
- Prefer simple V1 designs and avoid premature abstraction/over-engineering.
- The user wants to build the system incrementally and test against real CSVs early.
- CSV support comes first; PDF parsing is a future concern.

## 3. Current Development Status

Completed with tests:
- `HdfcBankAccountCsvParser`
- `HdfcCreditCardCsvParser`
- `ScapiaCreditCardCsvParser`
- HDFC UPI narration parser
- HDFC IMPS narration parser

The user is now eager to **load a real CSV and see the parsers working end-to-end** before returning to remaining architectural work.

Current next milestone:

```text
Real CSV
  ↓
Parser
  ↓
Normalized transactions
  ↓
Human-readable output
```

The database and UI should NOT be introduced yet.

---

# 4. High-Level Architecture

The intended flow is:

```text
CSV file
  ↓
Statement parser
  ↓
Raw statement row
  ↓
Generic statement fields + narration-specific fields
  ↓
NormalizedTransaction
  ↓
Mapping / enrichment
  ↓
Review
  ↓
Validation
  ↓
Submit
  ↓
Database
  ↓
Analysis
```

Parser responsibilities:

> What did the bank/card statement tell us?

Mapper responsibilities:

> Given what we know about the counterparty/context, what does this transaction mean?

Validator responsibilities:

> Does this transaction have enough valid information to be accepted?

Workflow state:

> Has the user reviewed/validated this transaction?

These concerns should remain separate.

---

# 5. Statement Parser Abstraction

The three completed parsers should eventually be usable through a common abstraction:

```text
StatementParser
    parse(File) → ParseResult
```

Current implementations:

```text
HdfcBankAccountCsvParser
HdfcCreditCardCsvParser
ScapiaCreditCardCsvParser
```

Conceptually:

```text
HdfcBankAccountCsvParser ─┐
HdfcCreditCardCsvParser ──┼──> StatementParser
ScapiaCreditCardCsvParser ┘
```

The caller should not need to know which concrete parser is being used.

Eventually, parser selection should be automatic through a parser registry:

```text
CSV
 ↓
Parser registry
 ↓
HdfcBankAccountCsvParser.canParse()
HdfcCreditCardCsvParser.canParse()
ScapiaCreditCardCsvParser.canParse()
 ↓
matching parser
 ↓
parse()
```

Do not over-engineer parser discovery in V1.

---

# 6. ParseResult

A parser should not return only:

```text
List<NormalizedTransaction>
```

because one malformed/unsupported row should not cause the whole import to fail.

Preferred conceptual model:

```text
ParseResult
├── transactions
├── issues
└── summary
```

Example:

```text
transactions = 147
issues       = 2

issues:
  row 38 → Unsupported narration
  row 91 → Malformed narration
```

Import should continue processing valid rows.

At the end, the user should see:
- number of transactions parsed
- number of issues
- details of issues
- optionally representative/complete normalized transactions

---

# 7. Import Runner — Immediate Next Task

Build a small CLI/import runner before introducing Spring UI, DB, or Notion.

Desired experience:

```text
> import /path/to/hdfc-aug-2026.csv
```

Conceptual output:

```text
Parsing: hdfc-aug-2026.csv
Parser: HdfcBankAccountCsvParser

Transactions parsed: 183
Parsing issues:       2

----------------------------------------
Date         Amount     Direction   Counterparty
----------------------------------------
01-08-2026   ₹35,000    CREDIT      Goldman Sachs
02-08-2026   ₹1,211     DEBIT       Swiggy
03-08-2026   ₹500       DEBIT       GUNDLURU MANOJ KUMAR
----------------------------------------

Import completed with 2 issues.
```

The immediate goal is simply:

> Real CSV → parser → normalized transaction objects → visible output.

This will likely expose useful design issues before database work begins.

---

# 8. NormalizedTransaction

The normalized model should be **bank-agnostic**.

Current conceptual fields:

```text
transactionDate      mandatory
valueDate            optional
amount               mandatory
direction            mandatory
transactionType      mandatory field; may initially be UNKNOWN
paymentMode          mandatory field; may be UNKNOWN
category             may initially be UNKNOWN
counterparty         may be UNKNOWN
counterpartyId       may be UNKNOWN
counterpartyType     may be UNKNOWN
userComment          optional
bankReference        optional
instrumentId         mandatory
rawRow               mandatory
reviewStatus         workflow metadata
```

`productReference` was explicitly rejected for V1.

Raw statement data should be preserved so normalized values can be verified against the original statement.

---

# 9. Direction vs Transaction Type

These are deliberately separate.

### Direction

Comes directly from statement fields such as Debit/Credit.

Examples:

```text
DEBIT
CREDIT
```

### Transaction Type

Represents the financial meaning.

Examples:

```text
PURCHASE
SALARY
REFUND
INTEREST
FEE
CREDIT_CARD_PAYMENT
LOAN_REPAYMENT
...
```

A transaction can therefore be:

```text
direction = CREDIT
transactionType = REFUND
```

or:

```text
direction = CREDIT
transactionType = CREDIT_CARD_PAYMENT
```

Direction should not be inferred from narration when the statement explicitly supplies debit/credit information.

---

# 10. Review Status vs UNKNOWN

Important architectural distinction:

### `UNKNOWN`

Describes the system's **knowledge**.

Example:

```text
transactionType = UNKNOWN
category = UNKNOWN
```

### `reviewStatus`

Describes the **workflow state**.

Example:

```text
reviewStatus = PENDING
```

`reviewStatus` belongs on `NormalizedTransaction`, not `NarrationParseResult`.

Example:

```text
counterparty = ANKIT KRIPA RAM
transactionType = UNKNOWN
reviewStatus = PENDING
```

Later:

```text
counterparty = ANKIT KRIPA RAM
transactionType = LOAN_REPAYMENT
reviewStatus = REVIEWED
```

The same counterparty can have different meanings for different transactions.

---

# 11. Narration Parser Architecture

HDFC bank-account parsing uses composition:

```text
HdfcBankAccountCsvParser
    HAS-A list of NarrationParser
```

Contract:

```text
NarrationParser
    canParse(String narration)
    parse(String narration)
```

Conceptual implementation list:

```text
UpiNarrationParser
ImpsNarrationParser
FtNarrationParser
TpNarrationParser
InterestNarrationParser
A2ANarrationParser
...
```

Parser selection is intentionally simple for V1:

```text
for parser in ordered list:
    if parser.canParse(narration):
        use first matching parser
```

Collision/priority handling is explicitly out of scope for now. List order determines the first match.

---

# 12. NarrationParseResult

The narration parser returns only information extracted from the narration:

```text
NarrationParseResult
├── narrationType
├── paymentMode
├── counterparty
├── counterpartyId
├── counterpartyType
├── userComment
└── bankReference
```

`narrationType` should conceptually be an enum rather than an arbitrary String.

Examples:

```text
UPI
IMPS
FT
TP
A2A
INTEREST
SWEEP_IN
...
```

`narrationType` and `paymentMode` are intentionally different.

Example:

```text
FT-XX5018-...-FTIMPS191676

narrationType = FT
paymentMode   = IMPS
```

Another:

```text
A2AINT01-GOLDMAN SACHS...
```

could be:

```text
narrationType = A2A
paymentMode   = UNKNOWN
```

`NarrationParseResult` should NOT contain:
- `reviewStatus`
- category
- transaction meaning decided by user mapping

Those belong later in the pipeline.

---

# 13. Error Handling

Three conceptual cases were identified:

## A. Unsupported narration

No narration parser recognizes it.

Use a dedicated parsing issue / exception concept such as:

```text
UnsupportedNarrationException
```

The row should be reported as an issue and import should continue.

## B. Malformed narration

A parser recognizes the format but cannot reliably extract the required information.

Use:

```text
MalformedNarrationException
```

Again, record the issue and continue.

## C. Meaning cannot be determined

This is NOT an exception.

Example:

```text
Ankit Kripa Ram
```

may represent:
- loan repayment
- Splitwise settlement
- gift
- miscellaneous transfer

The parser can successfully extract the counterparty while the mapping layer cannot confidently determine the financial meaning.

Represent this as:

```text
transactionType = UNKNOWN
category = UNKNOWN
reviewStatus = PENDING
```

rather than throwing an exception.

---

# 14. HDFC Bank CSV

Actual header supplied:

```text
Date, Narration, Value Dat, Debit Amount, Credit Amount, Chq/Ref Number, Closing Balance
```

Note: the user supplied the header as `Value Dat`; do not silently assume it is `Value Date` until verified.

Generic bank-parser responsibilities:
- file/schema validation
- CSV parsing
- raw-row preservation
- transaction date
- value date
- amount
- direction
- instrument ID
- narration parser selection
- combine generic fields with narration result

Direction should come from Debit/Credit columns.

---

# 15. UPI Parsing

Example:

```text
UPI-GUNDLURU MANOJ KUMAR-8618514430-2@AXL-CNRB0000033-659929884129-PRESS
```

Expected extraction:

```text
narrationType   = UPI
paymentMode     = UPI
counterparty    = GUNDLURU MANOJ KUMAR
counterpartyId  = 8618514430-2@AXL
counterpartyType = PERSON
userComment     = PRESS
bankReference   = 659929884129
```

Important parsing requirement:

The UPI ID can contain `-`, for example:

```text
8618514430-2@AXL
```

Therefore naive `split("-")` logic is fragile.

The parser should use structural anchors/grammar rather than assuming every hyphen creates a field boundary.

UPI RRN examples are 12-digit numeric references such as:

```text
659929884129
```

UPI parser has already been implemented and tested.

---

# 16. IMPS Parsing

Example:

```text
IMPS-622725189682-HRITIJ GUPTA-BARB-XXXXXXXXXX6144-ARCHITECT
```

Expected extraction:

```text
narrationType   = IMPS
paymentMode     = IMPS
bankReference   = 622725189682
counterparty    = HRITIJ GUPTA
bankShortName   = BARB
maskedAccount   = XXXXXXXXXX6144
userComment     = ARCHITECT
```

The conceptual counterparty ID for this type is:

```text
name + bank short name + masked account
```

IMPS parser has already been implemented and tested.

---

# 17. FT Parsing

Example:

```text
FT-XX5018-THANK YOU ALWAYS-ANKIT KRIPA RAM-FTIMPS191676
```

Expected interpretation:

```text
counterparty    = ANKIT KRIPA RAM
maskedAccount   = XX5018
userComment     = THANK YOU ALWAYS
paymentMode     = IMPS
bankReference   = FTIMPS191676
narrationType   = FT
```

The direction should come from the CSV Debit/Credit columns, not from the narration.

Counterparty identity:

```text
name + masked account
```

was explicitly confirmed as acceptable.

---

# 18. TP / Automated Debit-Credit

`TP-` represents an automated debit/credit.

Example:

```text
TP-MAX LIFE INS CO LTD-1972529402-2520
```

The parser can identify the statement structure, but the fact that this represents an insurance premium is a **mapping/business-meaning concern**, not something the low-level parser should hardcode.

---

# 19. Interest / Sweep Transactions

Examples:

```text
INT. ON SWCR ON-50301206241965
SWEEP-IN CREDIT - 50301206241965
MONTHLY INTEREST CREDIT 50301206241965
```

For:

```text
INT. ON SWCR ON-50301206241965
```

the counterparty is HDFC Bank and the counterparty type is BANK.

The number is a sweep/FD-related product identifier, but `productReference` was explicitly excluded from the V1 normalized model.

The original narration remains available through `rawRow`.

---

# 20. Salary / A2A

Example:

```text
A2AINT01-GOLDMAN SACHS SERVICES PRIVATE LIMITED-P-SALARY-SALARY AUGUST 2026
```

Generic parser interpretation:

```text
narrationType = A2A
counterparty = GOLDMAN SACHS SERVICES PRIVATE LIMITED
```

The parser should not hardcode that A2A means salary.

A user-specific mapping can establish:

```text
Goldman Sachs → SALARY
```

when that is an explicitly trusted mapping.

---

# 21. Counterparty and Mapping Design

`counterparty`:

> Human-readable payer/payee.

`counterpartyId`:

> Stable identity of that counterparty.

`counterpartyType`:

```text
PERSON
MERCHANT
ORGANIZATION
BANK
UNKNOWN
```

Counterparty identity should be separate from transaction meaning.

Examples of trusted user mappings:
- Manoj → Laundry/Ironing
- Hritij → Purchase
- Max Life → Insurance
- Goldman Sachs → Salary

But not every counterparty should be permanently assigned one meaning.

Example:

```text
Ankit Kripa Ram
```

could represent multiple purposes across different transactions.

Therefore mapping precedence should conceptually be:

```text
1. Transaction-specific mapping
2. Counterparty ID + comment/context mapping
3. Explicit trusted counterparty default
4. Deterministic statement classification
5. UNKNOWN / UNCATEGORIZED
```

This is a conceptual design; V1 does not need to implement all levels.

---

# 22. Category vs Transaction Type

Both belong to the normalized transaction.

Example:

```text
transactionType = PURCHASE
category        = Laundry & Ironing
```

Another:

```text
transactionType = SALARY
category        = Income
```

Another:

```text
transactionType = PURCHASE
category        = Insurance
```

Category assignment is a mapping/enrichment concern.

The validator should validate the result; it should not decide the category.

---

# 23. Credit Card Rules

Credit-card bill payment must NOT be treated as an expense when the credit-card statement is also being parsed.

Example HDFC CC row:

```text
BPPY CC PAYMENT PP0162 ...
AMT = 8,415.00
Debit / Credit = Cr
```

Should become conceptually:

```text
direction       = CREDIT
transactionType = CREDIT_CARD_PAYMENT
```

not:

```text
PURCHASE
```

A normal purchase such as:

```text
Swiggy Food BENGALURU
AMT = 1,211.00
Debit
```

should be:

```text
direction       = DEBIT
paymentMode     = CARD
counterparty    = Swiggy Food BENGALURU
transactionType = UNKNOWN initially
category        = UNKNOWN initially
```

Merchant/category interpretation remains a separate mapping problem.

---

# 24. Idempotency / Duplicate Handling

Once the database exists:
- imports should be idempotent
- duplicate transactions should not silently create duplicate records
- ambiguous potential duplicates should be flagged for review rather than silently discarded

This is not required for the immediate CSV demo but remains an important architectural requirement.

---

# 25. Refunds and Reversals

The future system must support:
- refunds
- reversals
- unmatched refunds/reversals
- refunds appearing in a later month

Do not assume every refund can be immediately linked to its original transaction.

Unmatched refunds/reversals should still be preserved.

Relationship linking is a future enrichment problem.

---

# 26. User Review Workflow

The intended V1 user workflow is completed in one session:

```text
Import
  ↓
Review
  ↓
Validate
  ↓
Submit
```

`Submit` should only proceed after review and validation are complete.

A parsing issue does not necessarily mean the entire import must fail; valid transactions can continue through the workflow while problematic rows remain flagged.

---

# 27. Future Database

The database should come after the real CSV import path is proven.

Preferred direction:
- lightweight embedded/local database
- local processing
- normalized transaction persistence
- idempotent imports
- support configured accounts/cards
- preserve raw rows
- store review/validation state

V1 may use hardcoded configured accounts/cards before configuration itself is persisted.

---

# 28. Account / Card Configuration

The system needs to know the user's own:
- bank accounts
- credit cards

`instrumentId` should uniquely identify an account/card because the user may have multiple instruments.

For credit-card parsers, do not derive the instrument identity from a customer name.

Conceptually:

```text
HdfcCreditCardCsvParser
    configured instrumentId
        ↓
NormalizedTransaction.instrumentId
```

---

# 29. Immediate Roadmap

## Now

Build:

```text
StatementParser
ParseResult
Import runner / CLI
```

Goal:

```text
Real CSV
  ↓
correct parser
  ↓
normalized transactions
  ↓
visible output
```

## Next

Add:
- parser registry / automatic parser selection
- structured parsing issues
- better import summary
- review/enrichment layer

## Then

Add:
- validation
- duplicate/idempotency
- embedded DB
- submit/persistence flow

## Later

Add:
- refunds/reversals linking
- analytics
- UI
- Notion integration/export
- PDF parsing

---

# 30. Architectural Principles to Preserve

1. **Parse first, interpret later.**
2. Keep bank-specific parsing separate from bank-agnostic normalized data.
3. Keep narration parsing separate from statement-column parsing.
4. Direction comes from explicit statement fields where available.
5. Transaction type represents meaning, not debit/credit direction.
6. Counterparty identity is separate from transaction meaning.
7. `UNKNOWN` and `reviewStatus` represent different concepts.
8. Parsing failures should be structured and reported, not silently ignored.
9. Business ambiguity should generally become a review state, not an exception.
10. Preserve raw input for verification.
11. Prefer simple V1 architecture over premature generalization.
12. Test against real statement formats.
13. Do not introduce the database until the real CSV flow is working.
14. Keep privacy/local-processing requirements central.
15. The user prefers to implement the Java themselves with architectural guidance rather than receiving full code dumps.
