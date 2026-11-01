package io.github.harshjoeyit.core.dedup;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.parse.model.TxnDirection;

import java.time.LocalDate;

/**
 * Instrument Id is intentionally kept out of Duplicate Key to indentify transaction
 * across Instruments. For example - user may accidentally upload statement of HDFC_SALARY_ACC
 * into instrument - HDFC_SAVINGS_ACC. Since parser would not flag this (both use same HDFC Acc parser)
 * This is part of a control to warn user.
 */
public record DuplicateKey(
        LocalDate date,
        Double amount,
        TxnDirection direction,
        String identifier // bankReference if present, else normalized counterparty
) {
    public static DuplicateKey from(NormalizedTransaction txn) {
        if (txn == null) {
            return null;
        }
        LocalDate date = txn.getDatetime() != null ? txn.getDatetime().toLocalDate() : null;

        String id = (txn.getBankReference() != null && !txn.getBankReference().isBlank())
                ? txn.getBankReference().trim().toUpperCase()
                : normalize(txn.getCounterparty());

        return new DuplicateKey(date, txn.getAmount(), txn.getDirection(), id);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toUpperCase();
    }
}
