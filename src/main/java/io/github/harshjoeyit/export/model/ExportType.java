package io.github.harshjoeyit.export.model;

import io.github.harshjoeyit.core.parse.model.TxnType;

import java.util.List;

public enum ExportType {
    PURCHASE(List.of(TxnType.PURCHASE, TxnType.REFUND)),
    INCOME(List.of(TxnType.INTEREST, TxnType.SALARY)),
    INVEST(List.of(TxnType.INVESTMENT)),
    LEND_OR_BORROWED(List.of(TxnType.LEND, TxnType.LEND_REPAYMENT, TxnType.BORROW, TxnType.BORROW_REPAYMENT)),
    CONSOLIDATED(List.of());

    private final List<TxnType> txnTypes;

    ExportType(List<TxnType> txnTypes) {
        this.txnTypes = txnTypes;
    }

    public List<TxnType> getTxnTypes() {
        return txnTypes;
    }
}