package io.github.harshjoeyit.enrichment.model;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Builder
public record OrderReceipt(
        String platform,
        String platformOrderId,
        String outletName,
        LocalDateTime orderDateTime,
        double totalAmount,
        List<OrderReceiptItem> items
) {
    public OrderReceipt {
        Objects.requireNonNull(platform, "platform must not be null");
        items = items != null ? List.copyOf(items) : Collections.emptyList();
    }
}
