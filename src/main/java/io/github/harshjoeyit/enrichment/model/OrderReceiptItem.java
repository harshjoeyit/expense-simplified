package io.github.harshjoeyit.enrichment.model;

import lombok.Builder;

import java.util.Objects;

@Builder
public record OrderReceiptItem(
        String itemName,
        int quantity,
        Double unitPrice,
        double totalPrice
) {
    public OrderReceiptItem {
        Objects.requireNonNull(itemName, "itemName must not be null");
        if (quantity <= 0) {
            quantity = 1;
        }
    }
}
