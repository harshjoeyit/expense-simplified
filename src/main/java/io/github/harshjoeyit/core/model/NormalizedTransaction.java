package io.github.harshjoeyit.core.model;

import io.github.harshjoeyit.core.parse.model.CounterpartyType;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Canonical normalized representation of a financial transaction across all sources.
 */
@Data
@Builder(toBuilder = true, buildMethodName = "buildInternal")
public class NormalizedTransaction {

    @Builder.Default
    private final UUID id = UUID.randomUUID();

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
    private UUID statementId;     // FK to Statement
    private String reviewRemarks;

    /**
     * User's individual share of the transaction if split (always >= 0).
     * If not split, defaults to {@link #amount}.
     */
    private Double myShare;

    // timestamps to draw lineage of updates done
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Double getMyShare() {
        return myShare != null ? myShare : amount;
    }

    public static class NormalizedTransactionBuilder {
        public NormalizedTransaction build() {
            if (this.myShare == null) {
                this.myShare = this.amount;
            }
            return buildInternal();
        }
    }
}
