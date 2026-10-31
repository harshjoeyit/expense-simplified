package io.github.harshjoeyit.split.repository;

import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.StatementReviewStatus;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnType;
import io.github.harshjoeyit.core.repository.JdbcStatementRepository;
import io.github.harshjoeyit.core.repository.JdbcTransactionRepository;
import io.github.harshjoeyit.split.model.SplitStatus;
import io.github.harshjoeyit.split.model.SplitStrategy;
import io.github.harshjoeyit.split.model.TransactionSplit;
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
        "app.persistence=sqlite",
        "spring.shell.interactive.enabled=false",
        "spring.datasource.url=jdbc:sqlite:target/test-expense-simplified.db?journal_mode=WAL&foreign_keys=ON"
})
@TestExecutionListeners(listeners = {
        DependencyInjectionTestExecutionListener.class
})
class JdbcSplitRepositoryTest {

    @Autowired
    private JdbcSplitRepository splitRepository;

    @Autowired
    private JdbcTransactionRepository transactionRepository;

    @Autowired
    private JdbcStatementRepository statementRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private UUID transactionId1;
    private UUID transactionId2;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM transaction_splits").update();
        jdbcClient.sql("DELETE FROM transactions").update();
        jdbcClient.sql("DELETE FROM statements").update();

        UUID statementId = UUID.randomUUID();
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

        transactionId1 = UUID.randomUUID();
        NormalizedTransaction txn1 = NormalizedTransaction.builder()
                .id(transactionId1)
                .statementId(statementId)
                .instrumentId("HDFC_ACC")
                .datetime(now)
                .amount(200.0)
                .direction(TxnDirection.DEBIT)
                .type(TxnType.PURCHASE)
                .reviewStatus(io.github.harshjoeyit.core.model.TxnReviewStatus.PENDING)
                .rawTxn("raw1")
                .createdAt(now)
                .updatedAt(now)
                .build();
        transactionRepository.saveTransaction(txn1);

        transactionId2 = UUID.randomUUID();
        NormalizedTransaction txn2 = NormalizedTransaction.builder()
                .id(transactionId2)
                .statementId(statementId)
                .instrumentId("HDFC_ACC")
                .datetime(now)
                .amount(100.0)
                .direction(TxnDirection.DEBIT)
                .type(TxnType.PURCHASE)
                .reviewStatus(io.github.harshjoeyit.core.model.TxnReviewStatus.PENDING)
                .rawTxn("raw2")
                .createdAt(now)
                .updatedAt(now)
                .build();
        transactionRepository.saveTransaction(txn2);
    }

    private TransactionSplit buildSplit(UUID id, UUID txnId, String friendId, double amount, SplitStatus status, LocalDateTime time) {
        return TransactionSplit.builder()
                .id(id)
                .transactionId(txnId)
                .friendId(friendId)
                .friendName(friendId.toUpperCase())
                .amount(amount)
                .strategy(SplitStrategy.EQUAL)
                .status(status)
                .createdAt(time)
                .updatedAt(time)
                .build();
    }

    @Test
    void saveAndFindById_returnsPersistedSplit() {
        UUID splitId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        TransactionSplit split = buildSplit(splitId, transactionId1, "ankit", 100.0, SplitStatus.PENDING, now);

        splitRepository.saveSplit(split);

        Optional<TransactionSplit> found = splitRepository.findById(splitId);
        assertThat(found).isPresent();
        TransactionSplit actual = found.get();
        assertThat(actual.id()).isEqualTo(splitId);
        assertThat(actual.transactionId()).isEqualTo(transactionId1);
        assertThat(actual.friendId()).isEqualTo("ankit");
        assertThat(actual.friendName()).isEqualTo("ANKIT");
        assertThat(actual.amount()).isEqualTo(100.0);
        assertThat(actual.strategy()).isEqualTo(SplitStrategy.EQUAL);
        assertThat(actual.status()).isEqualTo(SplitStatus.PENDING);
    }

    @Test
    void saveSplit_updatesExistingOnConflict_andPreservesImmutableFields() {
        UUID splitId = UUID.randomUUID();
        LocalDateTime originalTime = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime updatedTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        TransactionSplit original = buildSplit(splitId, transactionId1, "ankit", 100.0, SplitStatus.PENDING, originalTime);

        splitRepository.saveSplit(original);

        // Attempt to update mutable fields (amount, strategy, status, updatedAt)
        // and attempt to mutate immutable fields (transactionId, friendId, friendName, createdAt)
        TransactionSplit updateAttempt = TransactionSplit.builder()
                .id(splitId)
                .transactionId(transactionId2)
                .friendId("sachin")
                .friendName("Sachin Tendulkar")
                .amount(150.0)
                .strategy(SplitStrategy.CUSTOM)
                .status(SplitStatus.POSTED)
                .createdAt(updatedTime)
                .updatedAt(updatedTime)
                .build();

        splitRepository.saveSplit(updateAttempt);

        Optional<TransactionSplit> found = splitRepository.findById(splitId);
        assertThat(found).isPresent();
        TransactionSplit actual = found.get();

        // Mutable fields updated
        assertThat(actual.amount()).isEqualTo(150.0);
        assertThat(actual.strategy()).isEqualTo(SplitStrategy.CUSTOM);
        assertThat(actual.status()).isEqualTo(SplitStatus.POSTED);
        assertThat(actual.updatedAt()).isEqualTo(updatedTime);

        // Immutable relational and provenance fields preserved
        assertThat(actual.transactionId()).isEqualTo(transactionId1);
        assertThat(actual.friendId()).isEqualTo("ankit");
        assertThat(actual.friendName()).isEqualTo("ANKIT");
        assertThat(actual.createdAt()).isEqualTo(originalTime);
    }

    @Test
    void findSplitsByTxnId_returnsOnlySplitsForTargetTxn() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        TransactionSplit split1 = buildSplit(UUID.randomUUID(), transactionId1, "ankit", 100.0, SplitStatus.PENDING, now);
        TransactionSplit split2 = buildSplit(UUID.randomUUID(), transactionId1, "sachin", 100.0, SplitStatus.PENDING, now);
        TransactionSplit split3 = buildSplit(UUID.randomUUID(), transactionId2, "ankit", 50.0, SplitStatus.PENDING, now);

        splitRepository.saveSplits(List.of(split1, split2, split3));

        List<TransactionSplit> splits = splitRepository.findSplitsByTxnId(transactionId1);
        assertThat(splits).hasSize(2);
        assertThat(splits).extracting(TransactionSplit::friendId).containsExactlyInAnyOrder("ankit", "sachin");
    }

    @Test
    void deleteSplitsByTxnId_removesSplitsForMatchingTxnOnly() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        TransactionSplit split1 = buildSplit(UUID.randomUUID(), transactionId1, "ankit", 100.0, SplitStatus.PENDING, now);
        TransactionSplit split2 = buildSplit(UUID.randomUUID(), transactionId2, "sachin", 50.0, SplitStatus.PENDING, now);

        splitRepository.saveSplits(List.of(split1, split2));

        splitRepository.deleteSplitsByTxnId(transactionId1);

        assertThat(splitRepository.findSplitsByTxnId(transactionId1)).isEmpty();
        assertThat(splitRepository.findSplitsByTxnId(transactionId2)).hasSize(1);
    }

    @Test
    void findPendingSplits_respectsLimitAndStatus() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        TransactionSplit pending1 = buildSplit(UUID.randomUUID(), transactionId1, "ankit", 100.0, SplitStatus.PENDING, now.minusHours(2));
        TransactionSplit pending2 = buildSplit(UUID.randomUUID(), transactionId1, "sachin", 100.0, SplitStatus.PENDING, now.minusHours(1));
        TransactionSplit posted = buildSplit(UUID.randomUUID(), transactionId2, "rahul", 50.0, SplitStatus.POSTED, now);

        splitRepository.saveSplits(List.of(pending1, pending2, posted));

        List<TransactionSplit> pending = splitRepository.findPendingSplits(1);
        assertThat(pending).hasSize(1);
        // Ordered newest first
        assertThat(pending.get(0).id()).isEqualTo(pending2.id());
    }

    @Test
    void markAsPosted_updatesStatusToPosted() {
        UUID splitId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        TransactionSplit split = buildSplit(splitId, transactionId1, "ankit", 100.0, SplitStatus.PENDING, now);

        splitRepository.saveSplit(split);
        assertThat(splitRepository.findById(splitId).get().status()).isEqualTo(SplitStatus.PENDING);

        splitRepository.markAsPosted(splitId);

        Optional<TransactionSplit> posted = splitRepository.findById(splitId);
        assertThat(posted).isPresent();
        assertThat(posted.get().status()).isEqualTo(SplitStatus.POSTED);
    }
}
