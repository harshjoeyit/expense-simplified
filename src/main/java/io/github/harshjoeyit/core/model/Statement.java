package io.github.harshjoeyit.core.model;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Statement represents a csv,pdf statement
 */
@Builder
public record Statement (
    UUID id,                       // UUID
    FileType fileType,
    String filePath,
    String instrumentId,                // FK to Instrument
    StatementReviewStatus reviewStatus,  // e.g. PENDING_REVIEW, COMPLETED
    LocalDateTime createdAt,
    LocalDateTime updatedAt
){
    public static class StatementBuilder {
        private UUID id = UUID.randomUUID();
        private StatementReviewStatus reviewStatus = StatementReviewStatus.PENDING_REVIEW;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();
    }

    public Statement withMarkComplete() {
        return Statement.builder()
                .id(this.id)
                .filePath(this.filePath)
                .fileType(this.fileType)
                .instrumentId(this.instrumentId)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .reviewStatus(StatementReviewStatus.COMPLETED)
                .build();
    }
}
