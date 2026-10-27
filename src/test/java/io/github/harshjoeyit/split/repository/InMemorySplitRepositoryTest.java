package io.github.harshjoeyit.split.repository;

import io.github.harshjoeyit.split.model.SplitStatus;
import io.github.harshjoeyit.split.model.SplitStrategy;
import io.github.harshjoeyit.split.model.TransactionSplit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySplitRepositoryTest {

    private InMemorySplitRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemorySplitRepository();
    }

    @Test
    void deleteSplitsByTxnId_removesSplitsForMatchingTxnIdOnly() {
        UUID txn1 = UUID.randomUUID();
        UUID txn2 = UUID.randomUUID();

        TransactionSplit split1 = TransactionSplit.builder()
                .id(UUID.randomUUID())
                .transactionId(txn1)
                .friendId("ankit")
                .friendName("Ankit")
                .amount(100.0)
                .strategy(SplitStrategy.EQUAL)
                .status(SplitStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        TransactionSplit split2 = TransactionSplit.builder()
                .id(UUID.randomUUID())
                .transactionId(txn1)
                .friendId("sachin")
                .friendName("Sachin")
                .amount(100.0)
                .strategy(SplitStrategy.EQUAL)
                .status(SplitStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        TransactionSplit split3 = TransactionSplit.builder()
                .id(UUID.randomUUID())
                .transactionId(txn2)
                .friendId("ankit")
                .friendName("Ankit")
                .amount(50.0)
                .strategy(SplitStrategy.EQUAL)
                .status(SplitStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        repository.saveSplits(List.of(split1, split2, split3));
        assertThat(repository.findAll(10)).hasSize(3);

        repository.deleteSplitsByTxnId(txn1);

        List<TransactionSplit> remaining = repository.findAll(10);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).id()).isEqualTo(split3.id());
        assertThat(repository.findSplitsByTxnId(txn1)).isEmpty();
    }

    @Test
    void markAsPosted_updatesSplitStatusToPosted() {
        UUID splitId = UUID.randomUUID();
        TransactionSplit split = TransactionSplit.builder()
                .id(splitId)
                .transactionId(UUID.randomUUID())
                .friendId("ankit")
                .friendName("Ankit")
                .amount(100.0)
                .strategy(SplitStrategy.EQUAL)
                .status(SplitStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        repository.saveSplit(split);
        assertThat(repository.findById(splitId).get().status()).isEqualTo(SplitStatus.PENDING);

        repository.markAsPosted(splitId);
        assertThat(repository.findById(splitId).get().status()).isEqualTo(SplitStatus.POSTED);
    }
}
