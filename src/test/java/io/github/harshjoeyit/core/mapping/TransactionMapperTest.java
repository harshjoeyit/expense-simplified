package io.github.harshjoeyit.core.mapping;

import io.github.harshjoeyit.core.mapping.model.MappingRule;
import io.github.harshjoeyit.core.mapping.repository.MappingRuleRepository;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.model.CounterpartyType;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionMapperTest {

    @Mock
    private MappingRuleRepository mappingRuleRepository;

    @InjectMocks
    private TransactionMapper transactionMapper;

    @Test
    void enrich_withValidMapping_returnsEnrichedTransactionsAndPreservesOtherFields() {
        // Arrange: Create transactions with realistic parsed fields
        NormalizedTransaction swiggyTxn = createSampleTxn("Swiggy", "swiggy@icici", 250.0);
        NormalizedTransaction mkRetailTxn = createSampleTxn("M K Retail", null, 100.0);
        List<NormalizedTransaction> originalTransactions = List.of(swiggyTxn, mkRetailTxn);

        when(mappingRuleRepository.findByCounterpartyId("swiggy@icici")).thenReturn(Optional.of(
                MappingRule.builder()
                        .txnCategory(TxnCategory.DINE_OUT_OR_ORDER)
                        .txnType(TxnType.PURCHASE)
                        .autoReview(true)
                        .build()
        ));
        when(mappingRuleRepository.findByCounterpartyName("M K Retail")).thenReturn(Optional.of(
                MappingRule.builder()
                        .txnCategory(TxnCategory.SHOPPING)
                        .txnType(TxnType.PURCHASE)
                        .autoReview(false)
                        .build()
        ));

        // Act
        List<NormalizedTransaction> mappedTransactions = transactionMapper.enrich(originalTransactions);

        // Assert: First transaction enriched via ID rule and auto-reviewed
        assertThat(mappedTransactions).hasSize(2);
        NormalizedTransaction enrichedSwiggy = mappedTransactions.get(0);
        assertThat(enrichedSwiggy.getCategory()).isEqualTo(TxnCategory.DINE_OUT_OR_ORDER);
        assertThat(enrichedSwiggy.getType()).isEqualTo(TxnType.PURCHASE);
        assertThat(enrichedSwiggy.getReviewStatus()).isEqualTo(TxnReviewStatus.AUTO_REVIEWED);
        // Verify other parsed fields were preserved
        assertThat(enrichedSwiggy.getAmount()).isEqualTo(250.0);
        assertThat(enrichedSwiggy.getDirection()).isEqualTo(TxnDirection.DEBIT);
        assertThat(enrichedSwiggy.getMode()).isEqualTo(TxnMode.UPI);
        assertThat(enrichedSwiggy.getUserComment()).isEqualTo("Test Comment");
        assertThat(enrichedSwiggy.getBankReference()).isEqualTo("RRN123456789");
        assertThat(enrichedSwiggy.getInstrumentId()).isEqualTo("HDFC_ACC_001");

        // Assert: Second transaction enriched via Name rule and pending review (autoReview=false)
        NormalizedTransaction enrichedMkRetail = mappedTransactions.get(1);
        assertThat(enrichedMkRetail.getCategory()).isEqualTo(TxnCategory.SHOPPING);
        assertThat(enrichedMkRetail.getType()).isEqualTo(TxnType.PURCHASE);
        assertThat(enrichedMkRetail.getReviewStatus()).isEqualTo(TxnReviewStatus.PENDING);
        assertThat(enrichedMkRetail.getAmount()).isEqualTo(100.0);

        // Verify repository interactions
        verify(mappingRuleRepository).findByCounterpartyId("swiggy@icici");
        verify(mappingRuleRepository).findByCounterpartyName("M K Retail");
        verifyNoMoreInteractions(mappingRuleRepository);
    }

    @Test
    void enrich_whenNoRuleFound_returnsUnchangedTransaction() {
        // Arrange
        NormalizedTransaction txn1 = createSampleTxn("Unknown Seller", "unknown@icici", 250.0);
        NormalizedTransaction txn2 = createSampleTxn("Another Unknown", "abc@sbi", 100.0);
        List<NormalizedTransaction> originalTransactions = List.of(txn1, txn2);

        when(mappingRuleRepository.findByCounterpartyId("unknown@icici")).thenReturn(Optional.empty());
        when(mappingRuleRepository.findByCounterpartyName("Unknown Seller")).thenReturn(Optional.empty());
        when(mappingRuleRepository.findByCounterpartyId("abc@sbi")).thenReturn(Optional.empty());
        when(mappingRuleRepository.findByCounterpartyName("Another Unknown")).thenReturn(Optional.empty());

        // Act
        List<NormalizedTransaction> mappedTransactions = transactionMapper.enrich(originalTransactions);

        // Assert: Both transactions remain unmapped with original PENDING review status
        assertThat(mappedTransactions).hasSize(2);
        assertThat(mappedTransactions.get(0))
                .extracting(
                        NormalizedTransaction::getCategory,
                        NormalizedTransaction::getType,
                        NormalizedTransaction::getReviewStatus,
                        NormalizedTransaction::getAmount
                )
                .containsExactly(null, TxnType.UNKNOWN, TxnReviewStatus.PENDING, 250.0);

        assertThat(mappedTransactions.get(1))
                .extracting(
                        NormalizedTransaction::getCategory,
                        NormalizedTransaction::getType,
                        NormalizedTransaction::getReviewStatus,
                        NormalizedTransaction::getAmount
                )
                .containsExactly(null, TxnType.UNKNOWN, TxnReviewStatus.PENDING, 100.0);

        verify(mappingRuleRepository).findByCounterpartyId("unknown@icici");
        verify(mappingRuleRepository).findByCounterpartyName("Unknown Seller");
        verify(mappingRuleRepository).findByCounterpartyId("abc@sbi");
        verify(mappingRuleRepository).findByCounterpartyName("Another Unknown");
        verifyNoMoreInteractions(mappingRuleRepository);
    }

    @Test
    void enrich_whenCounterpartyIdIsNull_fallsBackToCounterpartyNameRule() {
        // Arrange: Case 1: counterpartyId is null, counterparty name exists and has a rule
        NormalizedTransaction txn = createSampleTxn("ABC Laundry", null, 250.0);

        when(mappingRuleRepository.findByCounterpartyName("ABC Laundry")).thenReturn(Optional.of(
                MappingRule.builder()
                        .txnCategory(TxnCategory.UTILITIES)
                        .txnType(TxnType.PURCHASE)
                        .autoReview(true)
                        .build()
        ));

        // Act
        List<NormalizedTransaction> mapped = transactionMapper.enrich(List.of(txn));

        // Assert
        assertThat(mapped).hasSize(1);
        assertThat(mapped.get(0).getCategory()).isEqualTo(TxnCategory.UTILITIES);
        assertThat(mapped.get(0).getType()).isEqualTo(TxnType.PURCHASE);
        assertThat(mapped.get(0).getReviewStatus()).isEqualTo(TxnReviewStatus.AUTO_REVIEWED);

        // findByCounterpartyId must NOT be called when ID is null
        verify(mappingRuleRepository, never()).findByCounterpartyId(anyString());
        verify(mappingRuleRepository).findByCounterpartyName("ABC Laundry");
        verifyNoMoreInteractions(mappingRuleRepository);
    }

    @Test
    void enrich_whenCounterpartyIdRuleNotFound_fallsBackToCounterpartyNameRule() {
        // Arrange: Case 2: counterpartyId exists but has NO rule -> falls back to counterparty name rule
        NormalizedTransaction txn = createSampleTxn("ABC Laundry", "laundry@upi", 100.0);

        when(mappingRuleRepository.findByCounterpartyId("laundry@upi")).thenReturn(Optional.empty());
        when(mappingRuleRepository.findByCounterpartyName("ABC Laundry")).thenReturn(Optional.of(
                MappingRule.builder()
                        .txnCategory(TxnCategory.UTILITIES)
                        .txnType(TxnType.PURCHASE)
                        .autoReview(true)
                        .build()
        ));

        // Act
        List<NormalizedTransaction> mapped = transactionMapper.enrich(List.of(txn));

        // Assert
        assertThat(mapped).hasSize(1);
        assertThat(mapped.get(0).getCategory()).isEqualTo(TxnCategory.UTILITIES);
        assertThat(mapped.get(0).getType()).isEqualTo(TxnType.PURCHASE);
        assertThat(mapped.get(0).getReviewStatus()).isEqualTo(TxnReviewStatus.AUTO_REVIEWED);

        // ID rule was checked first, then fallback to name rule
        verify(mappingRuleRepository).findByCounterpartyId("laundry@upi");
        verify(mappingRuleRepository).findByCounterpartyName("ABC Laundry");
        verifyNoMoreInteractions(mappingRuleRepository);
    }

    @Test
    void enrich_whenBothIdAndNameRulesExist_idRuleTakesPrecedence() {
        // Arrange: Both ID and Name rules exist, but specify DIFFERENT categories
        NormalizedTransaction txn = createSampleTxn("Swiggy Delivery", "swiggy@icici", 350.0);

        when(mappingRuleRepository.findByCounterpartyId("swiggy@icici")).thenReturn(Optional.of(
                MappingRule.builder()
                        .txnCategory(TxnCategory.DINE_OUT_OR_ORDER)
                        .txnType(TxnType.PURCHASE)
                        .autoReview(true)
                        .build()
        ));

        // Act
        List<NormalizedTransaction> mapped = transactionMapper.enrich(List.of(txn));

        // Assert: ID rule MUST win
        assertThat(mapped).hasSize(1);
        assertThat(mapped.get(0).getCategory()).isEqualTo(TxnCategory.DINE_OUT_OR_ORDER);

        // Name lookup must NOT be called because ID rule succeeded
        verify(mappingRuleRepository).findByCounterpartyId("swiggy@icici");
        verify(mappingRuleRepository, never()).findByCounterpartyName(anyString());
        verifyNoMoreInteractions(mappingRuleRepository);
    }

    @Test
    void enrich_withEmptyList_returnsEmptyList() {
        List<NormalizedTransaction> mapped = transactionMapper.enrich(List.of());

        assertThat(mapped).isEmpty();
        verifyNoInteractions(mappingRuleRepository);
    }

    @Test
    void enrich_whenCounterpartyAndIdAreBlank_doesNotQueryRepository() {
        NormalizedTransaction blankTxn = NormalizedTransaction.builder()
                .counterparty("   ")
                .counterpartyId("")
                .amount(50.0)
                .reviewStatus(TxnReviewStatus.PENDING)
                .type(TxnType.UNKNOWN)
                .build();

        List<NormalizedTransaction> mapped = transactionMapper.enrich(List.of(blankTxn));

        assertThat(mapped).hasSize(1);
        assertThat(mapped.get(0).getCategory()).isNull();
        verifyNoInteractions(mappingRuleRepository);
    }

    private NormalizedTransaction createSampleTxn(String counterparty, String counterpartyId, double amount) {
        return NormalizedTransaction.builder()
                .datetime(LocalDateTime.of(2026, 9, 1, 12, 0))
                .valueDatetime(LocalDateTime.of(2026, 9, 1, 12, 0))
                .amount(amount)
                .direction(TxnDirection.DEBIT)
                .type(TxnType.UNKNOWN)
                .mode(TxnMode.UPI)
                .counterparty(counterparty)
                .counterpartyId(counterpartyId)
                .counterpartyType(CounterpartyType.MERCHANT)
                .userComment("Test Comment")
                .bankReference("RRN123456789")
                .instrumentId("HDFC_ACC_001")
                .rawTxn("raw-csv-row")
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();
    }
}