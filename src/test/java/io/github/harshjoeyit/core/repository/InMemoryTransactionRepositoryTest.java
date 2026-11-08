package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.model.TxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTransactionRepositoryTest {

    private InMemoryTransactionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
    }

    @Test
    void saveTransaction_newTransaction_savesAndSetsTimestamps() {
        UUID txnId = UUID.randomUUID();
        UUID stmtId = UUID.randomUUID();

        NormalizedTransaction txn = NormalizedTransaction.builder()
                .statementId(stmtId)
                .amount(500.0)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        repository.saveTransaction(txn);

        Optional<NormalizedTransaction> retrieved = repository.findById(txn.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getCreatedAt()).isNotNull();
        assertThat(retrieved.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void saveTransaction_existingTransaction_updatesFieldsAndPreservesCreatedAt() throws InterruptedException {
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .type(TxnType.PURCHASE)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        repository.saveTransaction(txn);
        LocalDateTime initialCreatedAt = repository.findById(txn.getId()).get().getCreatedAt();
        LocalDateTime initialUpdatedAt = repository.findById(txn.getId()).get().getUpdatedAt();

        Thread.sleep(10); // Ensure timestamp advances

        // Update transaction
        NormalizedTransaction updatedTxn = txn.toBuilder()
                .category(TxnCategory.FOOD)
                .type(TxnType.PURCHASE)
                .reviewRemarks("Groceries")
                .reviewStatus(TxnReviewStatus.MANUAL_REVIEWED)
                .build();

        repository.saveTransaction(updatedTxn);

        Optional<NormalizedTransaction> result = repository.findById(txn.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getCategory()).isEqualTo(TxnCategory.FOOD);
        assertThat(result.get().getType()).isEqualTo(TxnType.PURCHASE);
        assertThat(result.get().getReviewRemarks()).isEqualTo("Groceries");
        assertThat(result.get().getReviewStatus()).isEqualTo(TxnReviewStatus.MANUAL_REVIEWED);
        assertThat(result.get().getCreatedAt()).isEqualTo(initialCreatedAt);
        assertThat(result.get().getUpdatedAt()).isAfter(initialUpdatedAt);
    }

    @Test
    void findPendingReviewTransactionsByStatementId_filtersOnlyPendingForStatement() {
        UUID stmt1 = UUID.randomUUID();
        UUID stmt2 = UUID.randomUUID();

        NormalizedTransaction pendingStmt1 = NormalizedTransaction.builder()
                .statementId(stmt1)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();
        NormalizedTransaction reviewedStmt1 = NormalizedTransaction.builder()
                .statementId(stmt1)
                .reviewStatus(TxnReviewStatus.AUTO_REVIEWED)
                .build();
        NormalizedTransaction pendingStmt2 = NormalizedTransaction.builder()
                .statementId(stmt2)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        repository.saveTransactions(List.of(pendingStmt1, reviewedStmt1, pendingStmt2));

        List<NormalizedTransaction> pending = repository.findPendingReviewTransactionsByStatementId(stmt1);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getId()).isEqualTo(pendingStmt1.getId());
    }

    @Test
    void findByDateRange_returnsTransactionsWithinRangeSortedByDatetime() {
        NormalizedTransaction t1 = NormalizedTransaction.builder()
                .datetime(LocalDateTime.parse("2024-08-01T10:00:00"))
                .amount(100.0)
                .build();
        NormalizedTransaction t2 = NormalizedTransaction.builder()
                .datetime(LocalDateTime.parse("2024-08-15T15:30:00"))
                .amount(200.0)
                .build();
        NormalizedTransaction t3 = NormalizedTransaction.builder()
                .datetime(LocalDateTime.parse("2024-08-31T20:00:00"))
                .amount(300.0)
                .build();
        NormalizedTransaction outOfRange = NormalizedTransaction.builder()
                .datetime(LocalDateTime.parse("2024-09-01T00:00:00"))
                .amount(400.0)
                .build();

        repository.saveTransactions(List.of(t3, t1, outOfRange, t2));

        List<NormalizedTransaction> result = repository.findByDateRange(
                LocalDateTime.parse("2024-08-01T00:00:00"),
                LocalDateTime.parse("2024-08-31T23:59:59")
        );

        assertThat(result).hasSize(3);
        assertThat(result).extracting(NormalizedTransaction::getAmount)
                .containsExactly(100.0, 200.0, 300.0);
    }

    @Test
    void findByDateRange_includesStartBoundaryAndExcludesEndBoundary() {
        LocalDateTime start = LocalDateTime.parse("2024-08-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2024-09-01T00:00:00");

        NormalizedTransaction atStart = NormalizedTransaction.builder()
                .datetime(start)
                .amount(10.0)
                .build();
        NormalizedTransaction inside = NormalizedTransaction.builder()
                .datetime(LocalDateTime.parse("2024-08-31T23:59:59"))
                .amount(20.0)
                .build();
        NormalizedTransaction atEnd = NormalizedTransaction.builder()
                .datetime(end)
                .amount(30.0)
                .build();

        repository.saveTransactions(List.of(atStart, inside, atEnd));

        List<NormalizedTransaction> result = repository.findByDateRange(start, end);

        assertThat(result).extracting(NormalizedTransaction::getAmount)
                .containsExactly(10.0, 20.0);
        assertThat(result).noneMatch(t -> t.getDatetime().equals(end));
    }
}
