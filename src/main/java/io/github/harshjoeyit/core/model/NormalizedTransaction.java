package io.github.harshjoeyit.core.model;

import io.github.harshjoeyit.core.parse.model.CounterpartyType;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@Data
public class NormalizedTransaction {
    private LocalDateTime datetime;
    private LocalDateTime valueDatetime;
    private Double amount;                  // Absolute amount
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
