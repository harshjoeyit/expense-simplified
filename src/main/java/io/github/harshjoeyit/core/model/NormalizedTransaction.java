package io.github.harshjoeyit.core.model;

import io.github.harshjoeyit.core.parse.model.CounterpartyType;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Canonical normalized representation of a financial transaction across all sources.
 */
@Builder
@Data
public class NormalizedTransaction {
    private LocalDateTime datetime;
    private LocalDateTime valueDatetime;

    /**
     * Absolute transaction amount (always >= 0).
     * The cash flow direction (inflow vs outflow) is captured exclusively by {@link #direction}.
     */
    private Double amount;
    private TxnDirection direction;
    private TxnType type;
    private TxnMode mode;
    private TxnCategory category;
    private String counterparty;
    private String counterpartyId;
    private CounterpartyType counterpartyType;
    private String userComment;
    private String bankReference;
    private String instrumentId;
    private TxnReviewStatus reviewStatus;
    private String rawTxn;
}
