package io.github.harshjoeyit.core.parse.model;

/**
 * Denotes the accounting / financial nature of this transaction. Ex - an expense, income, asset transfer, or debt.
 */
public enum TxnType {
    PURCHASE,                       // Category: FOOD, SHOPPING, UTILITIES, TRAVEL, DINE_OUT_OR_ORDER, FUN_AND_PLAY, FLIGHTS, INSURANCE, ETC
    REFUND,                         // Same as PURCHASE
    CREDIT_CARD_BILL_PAYMENT,       // N/A
    INTEREST,                       // Category: INCOME
    LEND,                           // N/A
    LEND_REPAYMENT,                 // N/A
    BORROW,                         // N/A
    BORROW_REPAYMENT,               // N/A
    SELF_ACCOUNT_TRANSFER,          // N/A
    INVESTMENT,                     // N/A
    SALARY                          // Category: INCOME
}
