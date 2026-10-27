package io.github.harshjoeyit.split.model;

import io.github.harshjoeyit.core.model.StatementReviewStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record TransactionSplit(
        UUID id,
        UUID transactionId,
        String friendId,
        String friendName,
        double amount,
        SplitStrategy strategy,
        SplitStatus status,
        LocalDateTime createdAt
) {
    public static class TransactionSplitBuilder {
        private UUID id = UUID.randomUUID();
        private SplitStatus status = SplitStatus.PENDING;
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    public TransactionSplit withMarkPosted() {
        return TransactionSplit.builder()
                .id(this.id)
                .transactionId(this.transactionId)
                .friendId(this.friendId)
                .friendName(this.friendName)
                .amount(this.amount)
                .strategy(this.strategy)
                .status(SplitStatus.POSTED)
                .createdAt(this.createdAt)
                .build();
    }
}