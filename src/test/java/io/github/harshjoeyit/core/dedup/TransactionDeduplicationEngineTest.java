package io.github.harshjoeyit.core.dedup;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionDeduplicationEngineTest {

    @Mock
    private TransactionRepository transactionRepository;

    private TransactionDeduplicationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TransactionDeduplicationEngine(transactionRepository);
    }

    @Test
    @DisplayName("Empty incoming list returns empty result")
    void deduplicate_emptyIncoming_returnsEmptyResult() {
        DeduplicationResult result = engine.deduplicate("HDFC_ACC", List.of());

        assertThat(result.newTransactions()).isEmpty();
        assertThat(result.duplicates()).isEmpty();
        assertThat(result.isFullDuplicate()).isFalse();
    }

    @Test
    @DisplayName("No existing transactions in date range -> all incoming transactions are marked as new")
    void deduplicate_noExistingTransactions_allMarkedAsNew() {
        NormalizedTransaction incoming = createTxn("HDFC_ACC", null, "2024-08-05T10:00:00", 500.0, TxnDirection.DEBIT, "REF123", "Swiggy");

        when(transactionRepository.findByDateRange(any(), any())).thenReturn(List.of());

        DeduplicationResult result = engine.deduplicate("HDFC_ACC", List.of(incoming));

        assertThat(result.newTransactions()).containsExactly(incoming);
        assertThat(result.duplicates()).isEmpty();
        assertThat(result.isFullDuplicate()).isFalse();
    }

    @Test
    @DisplayName("100% duplicate: all transactions exist in database -> isFullDuplicate() is true")
    void deduplicate_fullDuplicate_allMarkedAsDuplicates() {
        UUID existingStmtId = UUID.randomUUID();
        NormalizedTransaction existing1 = createTxn("HDFC_ACC", existingStmtId, "2024-08-01T10:00:00", 100.0, TxnDirection.DEBIT, "REF1", "Merchant A");
        NormalizedTransaction existing2 = createTxn("HDFC_ACC", existingStmtId, "2024-08-05T12:00:00", 200.0, TxnDirection.DEBIT, "REF2", "Merchant B");

        NormalizedTransaction incoming1 = createTxn("HDFC_ACC", null, "2024-08-01T10:00:00", 100.0, TxnDirection.DEBIT, "REF1", "Merchant A");
        NormalizedTransaction incoming2 = createTxn("HDFC_ACC", null, "2024-08-05T12:00:00", 200.0, TxnDirection.DEBIT, "REF2", "Merchant B");

        when(transactionRepository.findByDateRange(any(), any())).thenReturn(List.of(existing1, existing2));

        DeduplicationResult result = engine.deduplicate("HDFC_ACC", List.of(incoming1, incoming2));

        assertThat(result.isFullDuplicate()).isTrue();
        assertThat(result.newTransactions()).isEmpty();
        assertThat(result.duplicates()).hasSize(2);
        assertThat(result.statementSummaries()).hasSize(1);
        assertThat(result.statementSummaries().get(0).statementId()).isEqualTo(existingStmtId);
        assertThat(result.statementSummaries().get(0).duplicateCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Partial duplicates: skips existing and captures new transactions and statement summaries")
    void deduplicate_partialDuplicate_identifiesNewAndDuplicatesWithSummaries() {
        UUID stmtA = UUID.randomUUID();
        NormalizedTransaction existingAug1 = createTxn("HDFC_ACC", stmtA, "2024-08-01T10:00:00", 100.0, TxnDirection.DEBIT, "REF1", "Merchant A");

        NormalizedTransaction incomingAug1 = createTxn("HDFC_ACC", null, "2024-08-01T10:00:00", 100.0, TxnDirection.DEBIT, "REF1", "Merchant A");
        NormalizedTransaction incomingAug2 = createTxn("HDFC_ACC", null, "2024-08-02T11:00:00", 250.0, TxnDirection.DEBIT, "REF2", "Merchant B");

        when(transactionRepository.findByDateRange(any(), any())).thenReturn(List.of(existingAug1));

        DeduplicationResult result = engine.deduplicate("HDFC_ACC", List.of(incomingAug1, incomingAug2));

        assertThat(result.isFullDuplicate()).isFalse();
        assertThat(result.newTransactions()).containsExactly(incomingAug2);
        assertThat(result.duplicates()).hasSize(1);
        assertThat(result.duplicates().get(0).incoming()).isEqualTo(incomingAug1);
        assertThat(result.duplicates().get(0).existing()).isEqualTo(existingAug1);

        assertThat(result.statementSummaries()).hasSize(1);
        assertThat(result.statementSummaries().get(0).statementId()).isEqualTo(stmtA);
        assertThat(result.statementSummaries().get(0).duplicateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Multiset matching: handles legitimate identical transactions (e.g. two coffees on the same day)")
    void deduplicate_multisetMatching_handlesIdenticalPurchasesCorrectly() {
        UUID stmtA = UUID.randomUUID();
        // Database has 1 coffee purchase without a bank reference (Tier 2 match)
        NormalizedTransaction existingCoffee1 = createTxn("HDFC_CC", stmtA, "2024-08-05T14:30:00", 250.0, TxnDirection.DEBIT, null, "Starbucks");

        // Incoming statement has 2 coffee purchases on the same day
        NormalizedTransaction incomingCoffee1 = createTxn("HDFC_CC", null, "2024-08-05T14:30:00", 250.0, TxnDirection.DEBIT, null, "Starbucks");
        NormalizedTransaction incomingCoffee2 = createTxn("HDFC_CC", null, "2024-08-05T14:30:00", 250.0, TxnDirection.DEBIT, null, "Starbucks");

        when(transactionRepository.findByDateRange(any(), any())).thenReturn(List.of(existingCoffee1));

        DeduplicationResult result = engine.deduplicate("HDFC_CC", List.of(incomingCoffee1, incomingCoffee2));

        // 1st coffee matches the 1 existing in DB -> duplicate.
        // 2nd coffee has no remaining match in queue -> new transaction!
        assertThat(result.duplicates()).hasSize(1);
        assertThat(result.newTransactions()).hasSize(1);
    }

    @Test
    @DisplayName("Cross-instrument collision: detects if statement transactions belong to another instrument")
    void deduplicate_crossInstrumentCollision_detectsCollisionAndAborts() {
        UUID otherStmt = UUID.randomUUID();
        // Transactions already exist under HDFC_SALARY
        NormalizedTransaction existingSalary1 = createTxn("HDFC_SALARY", otherStmt,
                "2024-08-01T10:00:00", 100.0, TxnDirection.DEBIT, "REF1", "Merchant A");
        NormalizedTransaction existingSalary2 = createTxn("HDFC_SALARY", otherStmt,
                "2024-08-02T12:00:00", 200.0, TxnDirection.DEBIT, "REF2", "Merchant B");

        // User uploads the same file under HDFC_SAVINGS
        NormalizedTransaction incoming1 = createTxn("HDFC_SAVINGS", null,
                "2024-08-01T10:00:00", 100.0, TxnDirection.DEBIT, "REF1", "Merchant A");
        NormalizedTransaction incoming2 = createTxn("HDFC_SAVINGS", null,
                "2024-08-02T12:00:00", 200.0, TxnDirection.DEBIT, "REF2", "Merchant B");

        when(transactionRepository.findByDateRange(any(), any())).thenReturn(List.of(existingSalary1, existingSalary2));

        DeduplicationResult result = engine.deduplicate("HDFC_SAVINGS", List.of(incoming1, incoming2));

        assertThat(result.hasCrossInstrumentCollision()).isTrue();
        assertThat(result.crossInstrumentCollision()).isEqualTo("HDFC_SALARY");
    }

    private NormalizedTransaction createTxn(
            String instrumentId,
            UUID statementId,
            String datetime,
            Double amount,
            TxnDirection direction,
            String bankRef,
            String counterparty
    ) {
        return NormalizedTransaction.builder()
                .instrumentId(instrumentId)
                .statementId(statementId)
                .datetime(LocalDateTime.parse(datetime))
                .amount(amount)
                .direction(direction)
                .bankReference(bankRef)
                .counterparty(counterparty)
                .build();
    }
}
