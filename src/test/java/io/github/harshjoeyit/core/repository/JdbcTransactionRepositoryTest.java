package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.*;
import io.github.harshjoeyit.core.parse.model.CounterpartyType;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.shell.interactive.enabled=false",
        "spring.datasource.url=jdbc:sqlite:target/test-expense-simplified.db?journal_mode=WAL&foreign_keys=ON"
})
@TestExecutionListeners(listeners = {
        DependencyInjectionTestExecutionListener.class
})
class JdbcTransactionRepositoryTest {

    @Autowired
    private JdbcTransactionRepository transactionRepository;

    @Autowired
    private JdbcStatementRepository statementRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private UUID statementId;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM transaction_splits").update();
        jdbcClient.sql("DELETE FROM transactions").update();
        jdbcClient.sql("DELETE FROM statements").update();

        statementId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        Statement statement = Statement.builder()
                .id(statementId)
                .fileType(FileType.DELIMITED)
                .filePath("/test/statement.csv")
                .instrumentId("HDFC_ACC")
                .reviewStatus(StatementReviewStatus.PENDING_REVIEW)
                .createdAt(now)
                .updatedAt(now)
                .build();

        statementRepository.saveStatement(statement);
    }

    private NormalizedTransaction buildSampleTransaction(UUID txnId, LocalDateTime datetime, TxnReviewStatus status) {
        return NormalizedTransaction.builder()
                .id(txnId)
                .statementId(statementId)
                .instrumentId("HDFC_ACC")
                .datetime(datetime)
                .valueDatetime(datetime.plusDays(1))
                .amount(1250.50)
                .myShare(625.25)
                .direction(TxnDirection.DEBIT)
                .type(TxnType.PURCHASE)
                .mode(TxnMode.UPI)
                .category(TxnCategory.FOOD)
                .counterparty("Swiggy")
                .counterpartyId("swiggy@hdfc")
                .counterpartyType(CounterpartyType.MERCHANT)
                .userComment("Dinner order")
                .bankReference("UPI12345678")
                .reviewStatus(status)
                .reviewRemarks("Auto matched")
                .rawTxn("2026-10-31,Swiggy,1250.50")
                .createdAt(datetime)
                .updatedAt(datetime)
                .build();
    }

    @Test
    void saveAndFindById_returnsPersistedTransaction() {
        UUID txnId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        NormalizedTransaction txn = buildSampleTransaction(txnId, now, TxnReviewStatus.PENDING);

        transactionRepository.saveTransaction(txn);

        Optional<NormalizedTransaction> found = transactionRepository.findById(txnId);
        assertThat(found).isPresent();
        NormalizedTransaction actual = found.get();
        assertThat(actual.getId()).isEqualTo(txnId);
        assertThat(actual.getStatementId()).isEqualTo(statementId);
        assertThat(actual.getInstrumentId()).isEqualTo("HDFC_ACC");
        assertThat(actual.getAmount()).isEqualTo(1250.50);
        assertThat(actual.getMyShare()).isEqualTo(625.25);
        assertThat(actual.getDirection()).isEqualTo(TxnDirection.DEBIT);
        assertThat(actual.getType()).isEqualTo(TxnType.PURCHASE);
        assertThat(actual.getMode()).isEqualTo(TxnMode.UPI);
        assertThat(actual.getCategory()).isEqualTo(TxnCategory.FOOD);
        assertThat(actual.getCounterparty()).isEqualTo("Swiggy");
        assertThat(actual.getCounterpartyId()).isEqualTo("swiggy@hdfc");
        assertThat(actual.getCounterpartyType()).isEqualTo(CounterpartyType.MERCHANT);
        assertThat(actual.getUserComment()).isEqualTo("Dinner order");
        assertThat(actual.getBankReference()).isEqualTo("UPI12345678");
        assertThat(actual.getReviewStatus()).isEqualTo(TxnReviewStatus.PENDING);
        assertThat(actual.getReviewRemarks()).isEqualTo("Auto matched");
        assertThat(actual.getRawTxn()).isEqualTo("2026-10-31,Swiggy,1250.50");
    }

    @Test
    void saveTransaction_updatesExistingOnConflict_andPreservesCreatedAt() {
        UUID txnId = UUID.randomUUID();
        LocalDateTime originalTime = LocalDateTime.now().minusHours(5).truncatedTo(ChronoUnit.SECONDS);
        NormalizedTransaction txn = buildSampleTransaction(txnId, originalTime, TxnReviewStatus.PENDING);

        transactionRepository.saveTransaction(txn);

        // Update fields: resolve transaction and attempt to change immutable bank fields
        txn.setCategory(TxnCategory.DINE_OUT_OR_ORDER);
        txn.setReviewStatus(TxnReviewStatus.MANUAL_REVIEWED);
        txn.setReviewRemarks("User confirmed dining");
        txn.setAmount(99999.0); // should NOT be updated in DB
        txn.setRawTxn("tampered raw txn"); // should NOT be updated in DB
        txn.setBankReference("TAMPERED_REF"); // should NOT be updated in DB
        transactionRepository.saveTransaction(txn);

        Optional<NormalizedTransaction> found = transactionRepository.findById(txnId);
        assertThat(found).isPresent();
        NormalizedTransaction updated = found.get();
        // Mutable fields updated:
        assertThat(updated.getCategory()).isEqualTo(TxnCategory.DINE_OUT_OR_ORDER);
        assertThat(updated.getReviewStatus()).isEqualTo(TxnReviewStatus.MANUAL_REVIEWED);
        assertThat(updated.getReviewRemarks()).isEqualTo("User confirmed dining");
        // Immutable bank fields preserved untouched:
        assertThat(updated.getAmount()).isEqualTo(1250.50);
        assertThat(updated.getRawTxn()).isEqualTo("2026-10-31,Swiggy,1250.50");
        assertThat(updated.getBankReference()).isEqualTo("UPI12345678");
        assertThat(updated.getCreatedAt()).isEqualTo(originalTime);
    }

    @Test
    void findByStatementId_returnsAllTransactionsForStatement() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        NormalizedTransaction txn1 = buildSampleTransaction(UUID.randomUUID(), now.minusHours(2), TxnReviewStatus.PENDING);
        NormalizedTransaction txn2 = buildSampleTransaction(UUID.randomUUID(), now.minusHours(1), TxnReviewStatus.AUTO_REVIEWED);

        transactionRepository.saveTransactions(List.of(txn1, txn2));

        List<NormalizedTransaction> txns = transactionRepository.findByStatementId(statementId);
        assertThat(txns).hasSize(2);
        assertThat(txns.get(0).getId()).isEqualTo(txn1.getId());
        assertThat(txns.get(1).getId()).isEqualTo(txn2.getId());
    }

    @Test
    void findPendingReviewTransactionsByStatementId_returnsOnlyPendingInChronologicalOrder() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        NormalizedTransaction olderPending = buildSampleTransaction(UUID.randomUUID(), now.minusHours(2), TxnReviewStatus.PENDING);
        NormalizedTransaction newerPending = buildSampleTransaction(UUID.randomUUID(), now.minusHours(1), TxnReviewStatus.PENDING);
        NormalizedTransaction reviewed = buildSampleTransaction(UUID.randomUUID(), now.minusHours(3), TxnReviewStatus.MANUAL_REVIEWED);

        transactionRepository.saveTransactions(List.of(newerPending, reviewed, olderPending));

        List<NormalizedTransaction> pending = transactionRepository.findPendingReviewTransactionsByStatementId(statementId);
        assertThat(pending).hasSize(2);
        assertThat(pending.get(0).getId()).isEqualTo(olderPending.getId());
        assertThat(pending.get(1).getId()).isEqualTo(newerPending.getId());
    }

    @Test
    void findReviewedTransactionsByStatementId_returnsAutoAndManualReviewedInChronologicalOrder() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        NormalizedTransaction manualReviewed = buildSampleTransaction(UUID.randomUUID(), now.minusHours(2), TxnReviewStatus.MANUAL_REVIEWED);
        NormalizedTransaction autoReviewed = buildSampleTransaction(UUID.randomUUID(), now.minusHours(1), TxnReviewStatus.AUTO_REVIEWED);
        NormalizedTransaction pending = buildSampleTransaction(UUID.randomUUID(), now.minusHours(3), TxnReviewStatus.PENDING);

        transactionRepository.saveTransactions(List.of(autoReviewed, pending, manualReviewed));

        List<NormalizedTransaction> reviewed = transactionRepository.findReviewedTransactionsByStatementId(statementId);
        assertThat(reviewed).hasSize(2);
        assertThat(reviewed.get(0).getId()).isEqualTo(manualReviewed.getId());
        assertThat(reviewed.get(1).getId()).isEqualTo(autoReviewed.getId());
    }
}
