package io.github.harshjoeyit.core.model;

/**
 * Denotes where in the budget / lifestyle does this transaction belong. Only applies to spending/income)
 */
public enum TxnCategory {
    FOOD,
    SHOPPING,
    TRAVEL,
    RENT,
    DINE_OUT_OR_ORDER,  // Include Swiggy, Zomato
    UTILITIES,          // Ex - Soap, Deo, Electricity, Wifi, Mobile recharge
    INSURANCE,          // Life, health insurance
    FUN_AND_PLAY,       // Sports, Bowling, other activities
    FLIGHT,
    TRIP,               // Generic for all expenses done on trip (travel, food, hotel, etc)
    INCOME,
    SUBSCRIPTION,       // Netflix, Prime, etc
    PARTY,              // Drinks, food, etc
    MEDICINE,
    GIFT,
    MOVIE,
    MISCELLANEOUS
}
