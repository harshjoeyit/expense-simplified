package io.github.harshjoeyit.split.service;

import io.github.harshjoeyit.core.exception.InvalidTransactionId;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import io.github.harshjoeyit.split.InvalidSplitException;
import io.github.harshjoeyit.split.model.Friend;
import io.github.harshjoeyit.split.model.SplitInput;
import io.github.harshjoeyit.split.model.SplitStrategy;
import io.github.harshjoeyit.split.model.TransactionSplit;
import io.github.harshjoeyit.split.repository.SplitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SplitServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private SplitRepository splitRepository;

    private SplitService splitService;

    private final Friend alice = new Friend("alice", "Alice", "1234");
    private final Friend bob = new Friend("bob", "Bob", "5678");
    private final Friend self = Friend.SELF;

    @BeforeEach
    void setUp() {
        splitService = new SplitService(transactionRepository, splitRepository);
    }

    @Test
    void split_txnNotFound_throwsInvalidTransactionId() {
        UUID txnId = UUID.randomUUID();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.empty());

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.EQUAL)
                .valuePerFriend(Map.of(alice, 0.0, self, 0.0))
                .build();

        assertThatThrownBy(() -> splitService.split(txnId, input))
                .isInstanceOf(InvalidTransactionId.class);
    }

    @Test
    void split_amountDiffersFromTxnAmount_throwsInvalidSplitException() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        SplitInput input = SplitInput.builder()
                .amount(200.0)
                .strategy(SplitStrategy.EQUAL)
                .valuePerFriend(Map.of(alice, 0.0, self, 0.0))
                .build();

        assertThatThrownBy(() -> splitService.split(txnId, input))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("Transaction amount is different from total split amount");
    }

    @Test
    void splitEqual_withSelfIncluded_updatesMyShareAndAbsorbsRemainder() throws Exception {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        // 3 participants: 100 / 3 = 33.33 each, last absorbs remainder (33.34)
        Map<Friend, Double> participants = new LinkedHashMap<>();
        participants.put(alice, 0.0);
        participants.put(bob, 0.0);
        participants.put(self, 0.0);

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.EQUAL)
                .valuePerFriend(participants)
                .build();

        List<TransactionSplit> result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(txn.getMyShare()).isNotNull();

        // TransactionSplit should NOT contain SELF
        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(s -> s.friendId().equals("self"));

        double friendSum = result.stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(friendSum + txn.getMyShare()).isEqualTo(100.0);

        verify(splitRepository).saveSplits(result);
    }

    @Test
    void splitEqual_withoutSelf_setsMyShareToZero() throws Exception {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        Map<Friend, Double> participants = new LinkedHashMap<>();
        participants.put(alice, 0.0);
        participants.put(bob, 0.0);

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.EQUAL)
                .valuePerFriend(participants)
                .build();

        List<TransactionSplit> result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(txn.getMyShare()).isEqualTo(0.0);

        assertThat(result).hasSize(2);
        double sum = result.stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(sum).isEqualTo(100.0);
    }

    @Test
    void splitCustom_withValidAmounts_persistsSplits() throws Exception {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(150.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        Map<Friend, Double> participants = new LinkedHashMap<>();
        participants.put(self, 50.0);
        participants.put(alice, 60.0);
        participants.put(bob, 40.0);

        SplitInput input = SplitInput.builder()
                .amount(150.0)
                .strategy(SplitStrategy.CUSTOM)
                .valuePerFriend(participants)
                .build();

        List<TransactionSplit> result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(txn.getMyShare()).isEqualTo(50.0);

        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(s -> s.friendId().equals("self"));

        double friendSum = result.stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(friendSum).isEqualTo(100.0);
    }

    @Test
    void splitCustom_sumMismatch_throwsInvalidSplitException() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(150.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        Map<Friend, Double> participants = Map.of(
                self, 50.0,
                alice, 60.0
        ); // Sum = 110 != 150

        SplitInput input = SplitInput.builder()
                .amount(150.0)
                .strategy(SplitStrategy.CUSTOM)
                .valuePerFriend(participants)
                .build();

        assertThatThrownBy(() -> splitService.split(txnId, input))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("Total amount does not match aggregated split amount");
    }

    @Test
    void splitPercent_withValidPercentages_persistsSplitsAndAbsorbsRemainder() throws Exception {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        // 3 participants with 33.33%, 33.33%, 33.34%
        Map<Friend, Double> participants = new LinkedHashMap<>();
        participants.put(alice, 33.33);
        participants.put(bob, 33.33);
        participants.put(self, 33.34);

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.PERCENT)
                .valuePerFriend(participants)
                .build();

        List<TransactionSplit> result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(s -> s.friendId().equals("self"));

        double friendSum = result.stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(friendSum + txn.getMyShare()).isEqualTo(100.0);
    }

    @Test
    void splitPercent_sumNot100_throwsInvalidSplitException() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        Map<Friend, Double> participants = Map.of(
                alice, 40.0,
                self, 40.0
        ); // Sum = 80 != 100

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.PERCENT)
                .valuePerFriend(participants)
                .build();

        assertThatThrownBy(() -> splitService.split(txnId, input))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("Aggregated percent is not 100%");
    }

    @Test
    void splitShare_withValidRatios_persistsSplitsAndAbsorbsRemainder() throws Exception {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        // 3 equal shares of 100: 100 / 3 = 33.33 each, remainder absorbed by last
        Map<Friend, Double> participants = new LinkedHashMap<>();
        participants.put(alice, 1.0);
        participants.put(bob, 1.0);
        participants.put(self, 1.0);

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.SHARE)
                .valuePerFriend(participants)
                .build();

        List<TransactionSplit> result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(s -> s.friendId().equals("self"));

        double friendSum = result.stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(friendSum + txn.getMyShare()).isEqualTo(100.0);
    }

    @Test
    void split_onlySelfParticipant_throwsInvalidSplitException() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.EQUAL)
                .valuePerFriend(Map.of(self, 0.0))
                .build();

        assertThatThrownBy(() -> splitService.split(txnId, input))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("At least one friend (other than self) required for split");
    }

    @Test
    void splitPercent_withZeroPercent_throwsInvalidSplitException() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        Map<Friend, Double> participants = Map.of(
                alice, 0.0,
                self, 100.0
        );

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.PERCENT)
                .valuePerFriend(participants)
                .build();

        assertThatThrownBy(() -> splitService.split(txnId, input))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("Percent must be > 0 for all friends");
    }

    @Test
    void splitShare_withZeroShare_throwsInvalidSplitException() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        Map<Friend, Double> participants = Map.of(
                alice, 0.0,
                self, 1.0
        );

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.SHARE)
                .valuePerFriend(participants)
                .build();

        assertThatThrownBy(() -> splitService.split(txnId, input))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("Share must be > 0 for all friends");
    }

    @Test
    void splitCustom_withZeroAmount_throwsInvalidSplitException() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        Map<Friend, Double> participants = Map.of(
                alice, 0.0,
                self, 100.0
        );

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.CUSTOM)
                .valuePerFriend(participants)
                .build();

        assertThatThrownBy(() -> splitService.split(txnId, input))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("Amount must be > 0 for all friends");
    }

    @Test
    void split_deletesExistingSplitsForTransactionBeforeSavingNewSplits() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(100.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        Map<Friend, Double> participants = Map.of(
                alice, 0.0,
                self, 0.0
        );

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.EQUAL)
                .valuePerFriend(participants)
                .build();

        splitService.split(txnId, input);

        org.mockito.InOrder inOrder = inOrder(splitRepository);
        inOrder.verify(splitRepository).deleteSplitsByTxnId(txnId);
        inOrder.verify(splitRepository).saveSplits(anyList());
    }

    @Test
    void split_whenReSplittingTransaction_updatesMyShareToLatestValue() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(770.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        // 1st split: EQUAL among Alice, Bob, and Self (770 / 3 = 256.67, 256.67, 256.66)
        SplitInput equalInput = SplitInput.builder()
                .amount(770.0)
                .strategy(SplitStrategy.EQUAL)
                .valuePerFriend(Map.of(alice, 0.0, bob, 0.0, self, 0.0))
                .build();

        splitService.split(txnId, equalInput);
        assertThat(txn.getMyShare()).isBetween(256.66, 256.67);

        // 2nd split: SHARE with Self=1, Alice=2, Bob=1 (770 * 1/4 = 192.50)
        SplitInput shareInput = SplitInput.builder()
                .amount(770.0)
                .strategy(SplitStrategy.SHARE)
                .valuePerFriend(Map.of(self, 1.0, alice, 2.0, bob, 1.0))
                .build();

        splitService.split(txnId, shareInput);
        assertThat(txn.getMyShare()).isEqualTo(192.50);
    }
}
