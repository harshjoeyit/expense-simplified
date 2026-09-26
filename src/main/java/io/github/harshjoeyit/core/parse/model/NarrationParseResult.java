package io.github.harshjoeyit.core.parse.model;

import lombok.*;

@Builder
@Data
public class NarrationParseResult {
    private NarrationType narrationType; // UPI/IMS/TP
    private TxnMode txnMode;
    private String counterparty;
    private String counterpartyId;
    private CounterpartyType counterpartyType;
    private String userComment;
    private String bankReference;
}
