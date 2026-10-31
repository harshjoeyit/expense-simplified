package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.model.CounterpartyType;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Repository
@ConditionalOnProperty(name = "app.persistence", havingValue = "sqlite", matchIfMissing = true)
public class JdbcTransactionRepository implements TransactionRepository {

    private final JdbcClient jdbcClient;

    private static final RowMapper<NormalizedTransaction> ROW_MAPPER = (rs, rowNum) -> {
        String valueDatetimeStr = rs.getString("value_datetime");
        String modeStr = rs.getString("mode");
        String categoryStr = rs.getString("category");
        String counterpartyTypeStr = rs.getString("counterparty_type");
        String myShareStr = rs.getString("my_share");

        return NormalizedTransaction.builder()
                .id(UUID.fromString(rs.getString("id")))
                .statementId(UUID.fromString(rs.getString("statement_id")))
                .instrumentId(rs.getString("instrument_id"))
                .datetime(LocalDateTime.parse(rs.getString("datetime")))
                .valueDatetime(valueDatetimeStr != null ? LocalDateTime.parse(valueDatetimeStr) : null)
                .amount(rs.getDouble("amount"))
                .myShare(myShareStr != null ? rs.getDouble("my_share") : null)
                .direction(TxnDirection.valueOf(rs.getString("direction")))
                .type(TxnType.valueOf(rs.getString("type")))
                .mode(modeStr != null ? TxnMode.valueOf(modeStr) : null)
                .category(categoryStr != null ? TxnCategory.valueOf(categoryStr) : null)
                .counterparty(rs.getString("counterparty"))
                .counterpartyId(rs.getString("counterparty_id"))
                .counterpartyType(counterpartyTypeStr != null ? CounterpartyType.valueOf(counterpartyTypeStr) : null)
                .userComment(rs.getString("user_comment"))
                .bankReference(rs.getString("bank_reference"))
                .reviewStatus(TxnReviewStatus.valueOf(rs.getString("review_status")))
                .reviewRemarks(rs.getString("review_remarks"))
                .rawTxn(rs.getString("raw_txn"))
                .createdAt(LocalDateTime.parse(rs.getString("created_at")))
                .updatedAt(LocalDateTime.parse(rs.getString("updated_at")))
                .build();
    };

    public JdbcTransactionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<NormalizedTransaction> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbcClient.sql("SELECT * FROM transactions WHERE id = :id")
                .param("id", id.toString())
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public List<NormalizedTransaction> findByStatementId(UUID statementId) {
        return jdbcClient.sql("SELECT * FROM transactions WHERE statement_id = :statementId ORDER BY datetime ASC")
                .param("statementId", statementId.toString())
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public List<NormalizedTransaction> findPendingReviewTransactionsByStatementId(UUID statementId) {
        List<NormalizedTransaction> pending = jdbcClient
                .sql("""
                        SELECT * FROM transactions 
                        WHERE statement_id = :statementId 
                        AND review_status = :pendingStatus 
                        ORDER BY datetime ASC
                        """)
                .param("statementId", statementId.toString())
                .param("pendingStatus", TxnReviewStatus.PENDING.name())
                .query(ROW_MAPPER)
                .list();

        log.debug("found {} pending review transactions for statement: {}", pending.size(), statementId);

        return pending;
    }

    @Override
    public List<NormalizedTransaction> findReviewedTransactionsByStatementId(UUID statementId) {
        List<NormalizedTransaction> reviewed = jdbcClient
                .sql("""
                        SELECT * FROM transactions
                        WHERE statement_id = :statementId
                        AND review_status IN (:reviewedStatus)
                        ORDER BY datetime ASC
                        """)
                .param("statementId", statementId.toString())
                .param("reviewedStatus", List.of(TxnReviewStatus.MANUAL_REVIEWED.name(), TxnReviewStatus.AUTO_REVIEWED.name()))
                .query(ROW_MAPPER)
                .list();

        log.debug("found {} reviewed transactions for statement: {}", reviewed.size(), statementId);

        return reviewed;
    }

    @Override
    public void saveTransaction(NormalizedTransaction transaction) {
        if (transaction == null || transaction.getId() == null) {
            log.warn("Cannot save a null or save transaction without Id");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(now);
        }
        transaction.setUpdatedAt(now);

        /**
         * Facts parsed from the bank/card statement (statement_id, instrument_id,
         * datetime, value_datetime, amount, direction, bank_reference, raw_txn, created_at)
         * are intentionally immutable and excluded from updates.
         * Only user reviews, categorization, counterparty enrichments, and split shares can be mutated
         */
        String sql = """
                INSERT INTO transactions (
                    id, instrument_id, statement_id, datetime, value_datetime,
                    amount, my_share, direction, type, mode,
                    category, counterparty, counterparty_id, counterparty_type,
                    user_comment, bank_reference, review_status, review_remarks,
                    raw_txn, created_at, updated_at
                ) VALUES (
                    :id, :instrumentId, :statementId, :datetime, :valueDatetime,
                    :amount, :myShare, :direction, :type, :mode,
                    :category, :counterparty, :counterpartyId, :counterpartyType,
                    :userComment, :bankReference, :reviewStatus, :reviewRemarks,
                    :rawTxn, :createdAt, :updatedAt
                )
                ON CONFLICT(id) DO UPDATE SET
                    type              = excluded.type,
                    mode              = excluded.mode,
                    category          = excluded.category,
                    counterparty      = excluded.counterparty,
                    counterparty_id   = excluded.counterparty_id,
                    counterparty_type = excluded.counterparty_type,
                    user_comment      = excluded.user_comment,
                    review_status     = excluded.review_status,
                    review_remarks    = excluded.review_remarks,
                    my_share          = excluded.my_share,
                    updated_at        = excluded.updated_at
                """;

        jdbcClient.sql(sql)
                .param("id", transaction.getId().toString())
                .param("instrumentId", transaction.getInstrumentId())
                .param("statementId", transaction.getStatementId() != null ? transaction.getStatementId().toString() : null)
                .param("datetime", transaction.getDatetime().toString())
                .param("valueDatetime", transaction.getValueDatetime() != null ? transaction.getValueDatetime().toString() : null)
                .param("amount", transaction.getAmount())
                .param("myShare", transaction.getMyShare())
                .param("direction", transaction.getDirection() != null ? transaction.getDirection().name() : null)
                .param("type", transaction.getType() != null ? transaction.getType().name() : null)
                .param("mode", transaction.getMode() != null ? transaction.getMode().name() : null)
                .param("category", transaction.getCategory() != null ? transaction.getCategory().name() : null)
                .param("counterparty", transaction.getCounterparty())
                .param("counterpartyId", transaction.getCounterpartyId())
                .param("counterpartyType", transaction.getCounterpartyType() != null ? transaction.getCounterpartyType().name() : null)
                .param("userComment", transaction.getUserComment())
                .param("bankReference", transaction.getBankReference())
                .param("reviewStatus", transaction.getReviewStatus() != null ? transaction.getReviewStatus().name() : null)
                .param("reviewRemarks", transaction.getReviewRemarks())
                .param("rawTxn", transaction.getRawTxn() != null ? transaction.getRawTxn() : "")
                .param("createdAt", transaction.getCreatedAt().toString())
                .param("updatedAt", transaction.getUpdatedAt().toString())
                .update();

        log.debug("saved transaction: {}", transaction.getId());
    }
}
