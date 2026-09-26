package io.github.harshjoeyit.core.model;

import io.github.harshjoeyit.core.parse.model.CounterpartyType;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
import lombok.*;

import java.time.LocalDate;

@Builder
@Data
public class NormalizedTransaction {
    private LocalDate txnDate;
    private LocalDate valueDate;
    private Double amount;
    private TxnDirection direction;
    private TxnType txnType;
    private TxnMode txnMode;
    private TxnCategory txnCategory;
    private String counterparty;
    private String counterpartyId;
    private CounterpartyType counterpartyType;
    private String userComment;
    private String bankReference;
    private String instrumentId;
    private TxnReviewStatus txnReviewStatus;
    private RawCsvRow rawCsvRow;
}
