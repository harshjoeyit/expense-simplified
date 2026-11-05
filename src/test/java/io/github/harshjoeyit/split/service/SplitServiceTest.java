package io.github.harshjoeyit.split.service;

import io.github.harshjoeyit.core.exception.InvalidTransactionId;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import io.github.harshjoeyit.split.InvalidSplitException;
import io.github.harshjoeyit.split.model.Friend;
import io.github.harshjoeyit.split.model.SplitInput;
import io.github.harshjoeyit.split.model.SplitResult;
import io.github.harshjoeyit.split.model.SplitStrategy;
import io.github.harshjoeyit.split.model.TransactionSplit;
import io.github.harshjoeyit.split.repository.SplitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private final Friend alice = new Friend(1, "Alice", 1234L);
    private final Friend bob = new Friend(2, "Bob", 5678L);
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

        SplitResult result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(txn.getMyShare()).isNotNull();
        assertThat(result.myShare()).isEqualTo(txn.getMyShare());

        // TransactionSplit should NOT contain SELF
        assertThat(result.splits()).hasSize(2);
        assertThat(result.splits()).noneMatch(s -> s.friendId().equals(Friend.SELF.id()));

        double friendSum = result.splits().stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(friendSum + txn.getMyShare()).isEqualTo(100.0);

        verify(splitRepository).saveSplits(result.splits());
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

        SplitResult result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(txn.getMyShare()).isEqualTo(0.0);
        assertThat(result.myShare()).isEqualTo(0.0);

        assertThat(result.splits()).hasSize(2);
        double sum = result.splits().stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(sum).isEqualTo(100.0);
    }

    @Test
    void splitCustom_withValidAmounts_persistsSplits() throws Exception {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(150.10)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        // 50.02 + 60.00 + 40.08 = 150.10000000000002 in IEEE 754 floating-point
        Map<Friend, Double> participants = new LinkedHashMap<>();
        participants.put(self, 50.02);
        participants.put(alice, 60.00);
        participants.put(bob, 40.08);

        SplitInput input = SplitInput.builder()
                .amount(150.10)
                .strategy(SplitStrategy.CUSTOM)
                .valuePerFriend(participants)
                .build();

        SplitResult result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(txn.getMyShare()).isEqualTo(50.02);
        assertThat(result.myShare()).isEqualTo(50.02);

        assertThat(result.splits()).hasSize(2);
        assertThat(result.splits()).noneMatch(s -> s.friendId().equals(Friend.SELF.id()));

        double friendSum = result.splits().stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(friendSum).isCloseTo(100.08, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void splitCustom_withoutSelf_setsMyShareToZero() throws Exception {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(770.0)
                .build();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        Map<Friend, Double> participants = new LinkedHashMap<>();
        participants.put(alice, 200.0);
        participants.put(bob, 570.0);

        SplitInput input = SplitInput.builder()
                .amount(770.0)
                .strategy(SplitStrategy.CUSTOM)
                .valuePerFriend(participants)
                .build();

        SplitResult result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(txn.getMyShare()).isEqualTo(0.0);
        assertThat(result.myShare()).isEqualTo(0.0);
        assertThat(result.splits()).hasSize(2);
        double sum = result.splits().stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(sum).isEqualTo(770.0);
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

        // 46.59% + 34.40% + 19.01% = 100.00000000000001 in IEEE 754 floating-point
        Map<Friend, Double> participants = new LinkedHashMap<>();
        participants.put(alice, 46.59);
        participants.put(bob, 34.40);
        participants.put(self, 19.01);

        SplitInput input = SplitInput.builder()
                .amount(100.0)
                .strategy(SplitStrategy.PERCENT)
                .valuePerFriend(participants)
                .build();

        SplitResult result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(result.splits()).hasSize(2);
        assertThat(result.splits()).noneMatch(s -> s.friendId().equals(Friend.SELF.id()));

        double friendSum = result.splits().stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(friendSum + txn.getMyShare()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.myShare()).isEqualTo(txn.getMyShare());
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

        SplitResult result = splitService.split(txnId, input);

        verify(transactionRepository).saveTransaction(txn);
        assertThat(result.splits()).hasSize(2);
        assertThat(result.splits()).noneMatch(s -> s.friendId().equals(Friend.SELF.id()));

        double friendSum = result.splits().stream().mapToDouble(TransactionSplit::amount).sum();
        assertThat(friendSum + txn.getMyShare()).isEqualTo(100.0);
        assertThat(result.myShare()).isEqualTo(txn.getMyShare());
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
