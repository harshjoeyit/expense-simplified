package io.github.harshjoeyit.core.model;

/**
 * Denotes where in the budget / lifestyle does this transaction belong. Only applies to spending/income)
 */
public enum TxnCategory {
    FOOD,
    SHOPPING,
    TRAVEL,
    DINE_OUT_OR_ORDER,  // Include Swiggy, Zomato
    UTILITIES,          // Ex - Soap, Deo, Electricity, Wifi, Mobile recharge
    INSURANCE,          // Life, health insurance
    FUN_AND_PLAY,       // Sports, Bowling, other activities
    FLIGHT,
    INCOME,
}
