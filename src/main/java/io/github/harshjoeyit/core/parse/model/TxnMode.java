package io.github.harshjoeyit.core.parse.model;

public enum TxnMode {
    UPI,
    IMPS,
    TP,     // Third-party automatic transfer
    FT,     // Fund transfer
    A2A,    // Account to account internal transfer
    CREDIT_CARD, // Include RUPAY UPI
}
