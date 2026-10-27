package io.github.harshjoeyit.cli.command;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import io.github.harshjoeyit.split.model.SplitStatus;
import io.github.harshjoeyit.split.model.SplitStrategy;
import io.github.harshjoeyit.split.model.TransactionSplit;
import io.github.harshjoeyit.split.repository.SplitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SplitCommandsTest {

    @Mock
    private SplitRepository splitRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private SplitCommands splitCommands;

    @BeforeEach
    void setUp() {
        splitCommands = new SplitCommands(splitRepository, transactionRepository);
    }

    @Test
    void split_whenNoPendingSplits_returnsNoPendingSplitsMessage() {
        when(splitRepository.findPendingSplits(10)).thenReturn(List.of());

        String result = splitCommands.split(10, null, false, null);

        assertThat(result).isEqualTo("No pending splits found.");
    }

    @Test
    void split_whenNoSplitsWithAllFlag_returnsNoSplitsMessage() {
        when(splitRepository.findAll(10)).thenReturn(List.of());

        String result = splitCommands.split(10, null, true, null);

        assertThat(result).isEqualTo("No splits found.");
    }

    @Test
    void split_withPendingSplits_rendersTableWithDetails() {
        UUID splitId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 6, 21, 14, 30);

        TransactionSplit split = TransactionSplit.builder()
                .id(splitId)
                .transactionId(txnId)
                .friendId("ankit")
                .friendName("Ankit")
                .amount(250.00)
                .strategy(SplitStrategy.EQUAL)
                .status(SplitStatus.PENDING)
                .createdAt(now)
                .build();

        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .counterparty("Amazon")
                .amount(500.00)
                .datetime(now)
                .build();

        when(splitRepository.findPendingSplits(10)).thenReturn(List.of(split));
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        String result = splitCommands.split(10, null, false, null);

        assertThat(result).contains("Splits (1 shown):");
        assertThat(result).contains("Split ID");
        assertThat(result).contains("Txn ID");
        assertThat(result).contains(txnId.toString());
        assertThat(result).contains("Amazon");
        assertThat(result).contains("Ankit");
        assertThat(result).contains("₹250.00");
        assertThat(result).contains("EQUAL");
        assertThat(result).contains("PENDING");
    }

    @Test
    void split_withStatementFilter_filtersSplitsBelongingToStatement() {
        UUID stmtId = UUID.randomUUID();
        UUID txn1Id = UUID.randomUUID();
        UUID txn2Id = UUID.randomUUID();

        NormalizedTransaction txn1 = NormalizedTransaction.builder()
                .id(txn1Id)
                .statementId(stmtId)
                .counterparty("Uber")
                .build();

        TransactionSplit split1 = TransactionSplit.builder()
                .id(UUID.randomUUID())
                .transactionId(txn1Id)
                .friendName("Sachin")
                .amount(100.00)
                .status(SplitStatus.PENDING)
                .build();

        TransactionSplit split2 = TransactionSplit.builder()
                .id(UUID.randomUUID())
                .transactionId(txn2Id) // different txn
                .friendName("Ankit")
                .amount(50.00)
                .status(SplitStatus.PENDING)
                .build();

        when(transactionRepository.findByStatementId(stmtId)).thenReturn(List.of(txn1));
        when(splitRepository.findPendingSplits(Integer.MAX_VALUE)).thenReturn(List.of(split1, split2));
        when(transactionRepository.findById(txn1Id)).thenReturn(Optional.of(txn1));

        String result = splitCommands.split(10, stmtId, false, null);

        assertThat(result).contains("Splits (1 shown):");
        assertThat(result).contains("Sachin");
        assertThat(result).doesNotContain("Ankit");
    }

    @Test
    void split_withMarkPosted_marksSplitAsPosted() {
        UUID splitId = UUID.randomUUID();
        TransactionSplit split = TransactionSplit.builder()
                .id(splitId)
                .friendName("Ankit")
                .amount(150.00)
                .status(SplitStatus.PENDING)
                .build();

        when(splitRepository.findById(splitId)).thenReturn(Optional.of(split));

        String result = splitCommands.split(10, null, false, splitId);

        verify(splitRepository).markAsPosted(splitId);
        assertThat(result).contains("✓ Split " + splitId);
        assertThat(result).contains("Ankit owes ₹150.00");
        assertThat(result).contains("marked as POSTED");
    }

    @Test
    void split_withMarkPosted_whenSplitNotFound_returnsWarning() {
        UUID splitId = UUID.randomUUID();
        when(splitRepository.findById(splitId)).thenReturn(Optional.empty());

        String result = splitCommands.split(10, null, false, splitId);

        verify(splitRepository, never()).markAsPosted(any());
        assertThat(result).contains("⚠ Split not found with ID: " + splitId);
    }
}
