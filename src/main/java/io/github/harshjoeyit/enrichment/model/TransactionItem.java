package io.github.harshjoeyit.enrichment.model;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Builder
public record TransactionItem(
        Long id,
        UUID transactionId,
        String itemName,
        int quantity,
        Double unitPrice,
        double totalPrice,
        String outletName,
        String platform,
        String platformOrderId,
        LocalDateTime createdAt
) {
    public TransactionItem {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(itemName, "itemName must not be null");
        if (quantity <= 0) {
            quantity = 1;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public TransactionItem withId(Long id) {
        return new TransactionItem(
                id,
                this.transactionId,
                this.itemName,
                this.quantity,
                this.unitPrice,
                this.totalPrice,
                this.outletName,
                this.platform,
                this.platformOrderId,
                this.createdAt
        );
    }
}
