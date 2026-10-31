package io.github.harshjoeyit.split.repository;

import io.github.harshjoeyit.split.model.SplitStatus;
import io.github.harshjoeyit.split.model.SplitStrategy;
import io.github.harshjoeyit.split.model.TransactionSplit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@ConditionalOnProperty(name = "app.persistence", havingValue = "sqlite", matchIfMissing = true)
public class JdbcSplitRepository implements SplitRepository {

    private final JdbcClient jdbcClient;

    private static final RowMapper<TransactionSplit> ROW_MAPPER = (rs, rowNum) -> TransactionSplit.builder()
            .id(UUID.fromString(rs.getString("id")))
            .transactionId(UUID.fromString(rs.getString("transaction_id")))
            .friendId(rs.getString("friend_id"))
            .friendName(rs.getString("friend_name"))
            .amount(rs.getDouble("amount"))
            .strategy(SplitStrategy.valueOf(rs.getString("strategy")))
            .status(SplitStatus.valueOf(rs.getString("status")))
            .createdAt(LocalDateTime.parse(rs.getString("created_at")))
            .updatedAt(LocalDateTime.parse(rs.getString("updated_at")))
            .build();

    public JdbcSplitRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<TransactionSplit> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbcClient.sql("SELECT * FROM transaction_splits WHERE id = :id")
                .param("id", id.toString())
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public List<TransactionSplit> findSplitsByTxnId(UUID txnId) {
        if (txnId == null) {
            return List.of();
        }
        return jdbcClient.sql("SELECT * FROM transaction_splits WHERE transaction_id = :txnId ORDER BY created_at ASC")
                .param("txnId", txnId.toString())
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public List<TransactionSplit> findPendingSplits(int limit) {
        if (limit <= 0) {
            log.warn("Invalid limit: {}. Expected a positive integer.", limit);
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT * FROM transaction_splits 
                        WHERE status = :status 
                        ORDER BY created_at DESC 
                        LIMIT :limit
                        """)
                .param("status", SplitStatus.PENDING.name())
                .param("limit", limit)
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public List<TransactionSplit> findAll(int limit) {
        if (limit <= 0) {
            log.warn("Invalid limit: {}. Expected a positive integer.", limit);
            return List.of();
        }
        return jdbcClient.sql("SELECT * FROM transaction_splits ORDER BY created_at DESC LIMIT :limit")
                .param("limit", limit)
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public void deleteSplitsByTxnId(UUID txnId) {
        if (txnId == null) {
            log.warn("Failed to delete splits. Expected txnId to be non-null");
            return;
        }
        jdbcClient.sql("DELETE FROM transaction_splits WHERE transaction_id = :txnId")
                .param("txnId", txnId.toString())
                .update();
        log.debug("Deleted splits for txnId: {}", txnId);
    }

    @Override
    public void saveSplit(TransactionSplit split) {
        if (split == null || split.id() == null) {
            log.warn("Failed to save. Invalid split: {}. Expected non-null split and ID", split);
            return;
        }

        // Note: transaction_id, friend_id, friend_name, and created_at are immutable once created.
        // A split line is strictly bound to (transaction, friend). Only calculation and status fields
        // (amount, strategy, status, updated_at) can be updated.
        // Modifying participants requires deleting and recreating splits (as handled in SplitService.split()).
        String sql = """
                INSERT INTO transaction_splits (
                    id, transaction_id, friend_id, friend_name, amount, strategy, status, created_at, updated_at
                ) VALUES (
                    :id, :transactionId, :friendId, :friendName, :amount, :strategy, :status, :createdAt, :updatedAt
                )
                ON CONFLICT(id) DO UPDATE SET
                    amount      = excluded.amount,
                    strategy    = excluded.strategy,
                    status      = excluded.status,
                    updated_at  = excluded.updated_at
                """;

        LocalDateTime createdAt = split.createdAt() != null ? split.createdAt() : LocalDateTime.now();
        LocalDateTime updatedAt = split.updatedAt() != null ? split.updatedAt() : LocalDateTime.now();

        jdbcClient.sql(sql)
                .param("id", split.id().toString())
                .param("transactionId", split.transactionId().toString())
                .param("friendId", split.friendId())
                .param("friendName", split.friendName())
                .param("amount", split.amount())
                .param("strategy", split.strategy().name())
                .param("status", split.status().name())
                .param("createdAt", createdAt.toString())
                .param("updatedAt", updatedAt.toString())
                .update();

        log.debug("Saved split: {}", split.id());
    }

    @Override
    public void markAsPosted(UUID splitId) {
        if (splitId == null) {
            log.warn("Cannot mark null splitId as posted");
            return;
        }

        String sql = """
                UPDATE transaction_splits
                SET status = :status, updated_at = :updatedAt
                WHERE id = :id
                """;

        int rowsUpdated = jdbcClient.sql(sql)
                .param("status", SplitStatus.POSTED.name())
                .param("updatedAt", LocalDateTime.now().toString())
                .param("id", splitId.toString())
                .update();

        if (rowsUpdated == 0) {
            log.error("Failed to mark posted. Could not find split: {}", splitId);
        } else {
            log.debug("Marked split as posted: {}", splitId);
        }
    }
}
