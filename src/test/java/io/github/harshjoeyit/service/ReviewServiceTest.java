package io.github.harshjoeyit.service;

import io.github.harshjoeyit.core.exception.InvalidMappingRule;
import io.github.harshjoeyit.core.exception.InvalidTransactionId;
import io.github.harshjoeyit.core.mapping.model.MappingRule;
import io.github.harshjoeyit.core.mapping.repository.MappingRuleRepository;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.SaveRule;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.StatementReviewStatus;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.model.TxnType;
import io.github.harshjoeyit.core.repository.StatementRepository;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MappingRuleRepository mappingRuleRepository;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(statementRepository, transactionRepository, mappingRuleRepository);
    }

    @Test
    void findPendingReviewTransaction_nullStatementId_findsLatestAndReturnsTransactions() {
        // Arrange
        UUID statementId = UUID.randomUUID();
        Statement statement = Statement.builder().id(statementId).build();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .statementId(statementId)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        when(statementRepository.findLatest()).thenReturn(Optional.of(statement));
        when(transactionRepository.findPendingReviewTransactionsByStatementId(statementId))
                .thenReturn(List.of(txn));

        // Act
        List<NormalizedTransaction> result = reviewService.findPendingReviewTransaction(null);

        // Assert
        assertThat(result).containsExactly(txn);
        verify(statementRepository).findLatest();
        verify(transactionRepository).findPendingReviewTransactionsByStatementId(statementId);
    }

    @Test
    void findPendingReviewTransaction_withStatementId_findsByIdAndReturnsTransactions() {
        // Arrange
        UUID statementId = UUID.randomUUID();
        Statement statement = Statement.builder().id(statementId).build();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .statementId(statementId)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        when(statementRepository.findById(statementId)).thenReturn(Optional.of(statement));
        when(transactionRepository.findPendingReviewTransactionsByStatementId(statementId))
                .thenReturn(List.of(txn));

        // Act
        List<NormalizedTransaction> result = reviewService.findPendingReviewTransaction(statementId);

        // Assert
        assertThat(result).containsExactly(txn);
        verify(statementRepository).findById(statementId);
        verify(transactionRepository).findPendingReviewTransactionsByStatementId(statementId);
    }

    @Test
    void findPendingReviewTransaction_statementNotFound_throwsNoSuchElementException() {
        // Arrange
        UUID statementId = UUID.randomUUID();
        when(statementRepository.findById(statementId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> reviewService.findPendingReviewTransaction(statementId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Statement not found");
    }

    @Test
    void findPendingReviewTransaction_latestStatementNotFound_throwsNoSuchElementException() {
        // Arrange
        when(statementRepository.findLatest()).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> reviewService.findPendingReviewTransaction(null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Statement not found");
    }

    @Test
    void findPendingReviewTransaction_noTransactionsFound_returnsEmpty() {
        // Arrange
        UUID statementId = UUID.randomUUID();
        Statement statement = Statement.builder().id(statementId).build();

        when(statementRepository.findById(statementId)).thenReturn(Optional.of(statement));
        when(transactionRepository.findPendingReviewTransactionsByStatementId(statementId))
                .thenReturn(Collections.emptyList());

        // Act
        List<NormalizedTransaction> result = reviewService.findPendingReviewTransaction(statementId);

        // Assert
        assertThat(result).isEmpty();
    }

    // Tests for resolveTransaction

    @Test
    void resolveTransaction_transactionFound_updatesOnlyProvidedFields() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .statementId(statementId)
                .category(TxnCategory.FOOD)
                .type(TxnType.PURCHASE)
                .reviewRemarks("Existing remark")
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(transactionRepository.findPendingReviewTransactionsByStatementId(statementId))
                .thenReturn(List.of(NormalizedTransaction.builder().build()));

        // Act
        reviewService.resolveTransaction(txnId, null, TxnCategory.SHOPPING, "   ", null);

        // Assert
        assertThat(txn.getCategory()).isEqualTo(TxnCategory.SHOPPING);
        assertThat(txn.getType()).isEqualTo(TxnType.PURCHASE);
        assertThat(txn.getReviewRemarks()).isEqualTo("Existing remark");
        assertThat(txn.getReviewStatus()).isEqualTo(TxnReviewStatus.MANUAL_REVIEWED);

        verify(transactionRepository).saveTransaction(txn);
        // No rule saved and statement review status not updated
        verifyNoInteractions(mappingRuleRepository);
        verifyNoInteractions(statementRepository);
    }

    @Test
    void resolveTransaction_saveRule_updatesTransactionAndSavesRule() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .counterpartyId("CP-123")
                .statementId(statementId)
                .category(TxnCategory.FOOD)
                .type(TxnType.UNKNOWN)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        SaveRule saveRule = SaveRule.builder()
                .saveRuleByType(SaveRule.SaveRuleByType.COUNTERPARTY_ID)
                .id("CP-123")
                .autoReview(true)
                .build();

        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(transactionRepository.findPendingReviewTransactionsByStatementId(statementId))
                .thenReturn(List.of(NormalizedTransaction.builder().build()));

        // Act
        reviewService.resolveTransaction(txnId, TxnType.PURCHASE, TxnCategory.UTILITIES,
                "Electricity bill", saveRule);

        // Assert
        assertThat(txn.getCategory()).isEqualTo(TxnCategory.UTILITIES);
        assertThat(txn.getType()).isEqualTo(TxnType.PURCHASE);
        assertThat(txn.getReviewRemarks()).isEqualTo("Electricity bill");
        assertThat(txn.getReviewStatus()).isEqualTo(TxnReviewStatus.MANUAL_REVIEWED);

        verify(transactionRepository).saveTransaction(txn);

        MappingRule expectedRule = MappingRule.builder()
                .txnCategory(TxnCategory.UTILITIES)
                .txnType(TxnType.PURCHASE)
                .autoReview(true)
                .build();
        verify(mappingRuleRepository).saveByCounterpartyId("CP-123", expectedRule);
    }

    @Test
    void resolveTransaction_incompleteSaveRule_throwsInvalidMappingRuleException() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .counterpartyId("CP-123")
                .statementId(UUID.randomUUID())
                .category(TxnCategory.FOOD)
                .type(TxnType.PURCHASE)
                .build();

        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));

        SaveRule incompleteSaveRule = SaveRule.builder()
                .saveRuleByType(SaveRule.SaveRuleByType.COUNTERPARTY_ID)
                .id("")
                .autoReview(true)
                .build();

        // Act & Assert
        assertThatThrownBy(() -> reviewService.resolveTransaction(txnId, TxnType.PURCHASE, TxnCategory.SHOPPING, "remark", incompleteSaveRule))
                .isInstanceOf(InvalidMappingRule.class)
                .hasMessageStartingWith("Save rule is incomplete. Type and ID are required:");

        verifyNoInteractions(mappingRuleRepository);
        verifyNoInteractions(statementRepository);
    }


    @Test
    void resolveTransaction_lastReviewedTransaction_updatesStatementAsReviewComplete() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .statementId(statementId)
                .category(TxnCategory.FOOD)
                .type(TxnType.PURCHASE)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();
        Statement statement = Statement.builder()
                .id(statementId)
                .reviewStatus(StatementReviewStatus.PENDING_REVIEW)
                .build();

        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(transactionRepository.findPendingReviewTransactionsByStatementId(statementId))
                .thenReturn(Collections.emptyList());
        when(statementRepository.findById(statementId)).thenReturn(Optional.of(statement));

        // Act
        reviewService.resolveTransaction(txnId, TxnType.PURCHASE, TxnCategory.SHOPPING, "Reviewed", null);

        // Assert
        verify(transactionRepository).saveTransaction(txn);
        verify(statementRepository).findById(statementId);

        ArgumentCaptor<Statement> statementCaptor = ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository).saveStatement(statementCaptor.capture());
        Statement savedStatement = statementCaptor.getValue();
        // review status should be complete for the statement
        assertThat(savedStatement.id()).isEqualTo(statementId);
        assertThat(savedStatement.reviewStatus()).isEqualTo(StatementReviewStatus.COMPLETED);
    }

    @Test
    void resolveTransaction_moreTransactionToBeReviewed_doesNoUpdateStatementAsReviewComplete() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .statementId(statementId)
                .category(TxnCategory.FOOD)
                .type(TxnType.PURCHASE)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();
        NormalizedTransaction remainingTxn = NormalizedTransaction.builder()
                .statementId(statementId)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(transactionRepository.findPendingReviewTransactionsByStatementId(statementId))
                .thenReturn(List.of(remainingTxn));

        // Act
        reviewService.resolveTransaction(txnId, TxnType.PURCHASE, TxnCategory.SHOPPING, "Reviewed", null);

        // Assert
        verify(transactionRepository).saveTransaction(txn);
        verifyNoInteractions(statementRepository);
    }

    @Test
    void resolveTransaction_noTransactionFound_throwsInvalidTransactionIdException() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> reviewService.resolveTransaction(txnId, TxnType.PURCHASE, TxnCategory.FOOD, "Remark", null))
                .isInstanceOf(InvalidTransactionId.class)
                .hasMessage("transaction" + txnId + " not found");

        // Assert
        verify(transactionRepository, never()).saveTransaction(any());
        verifyNoInteractions(statementRepository);
        verifyNoInteractions(mappingRuleRepository);
    }
}
