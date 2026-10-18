package io.github.harshjoeyit.service;

import io.github.harshjoeyit.core.mapping.TransactionMapper;
import io.github.harshjoeyit.core.mapping.config.MappingConfig;
import io.github.harshjoeyit.core.mapping.model.MappingRule;
import io.github.harshjoeyit.core.mapping.repository.InMemoryMappingRuleRepository;
import io.github.harshjoeyit.core.model.*;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnType;
import io.github.harshjoeyit.core.repository.InMemoryStatementRepository;
import io.github.harshjoeyit.core.repository.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewWorkflowIntegrationTest {

    private InMemoryStatementRepository statementRepository;
    private InMemoryTransactionRepository transactionRepository;
    private InMemoryMappingRuleRepository mappingRuleRepository;
    private ReviewService reviewService;
    private TransactionMapper transactionMapper;

    @BeforeEach
    void setUp() {
        statementRepository = new InMemoryStatementRepository();
        transactionRepository = new InMemoryTransactionRepository();
        mappingRuleRepository = new InMemoryMappingRuleRepository(new MappingConfig(Map.of(), Map.of()));
        reviewService = new ReviewService(statementRepository, transactionRepository, mappingRuleRepository);
        transactionMapper = new TransactionMapper(mappingRuleRepository);
    }

    @Test
    void resolveTransaction_persistsReviewedFieldsAndPreservesCreatedAt() throws InterruptedException {
        UUID statementId = UUID.randomUUID();
        Statement statement = Statement.builder()
                .id(statementId)
                .reviewStatus(StatementReviewStatus.PENDING_REVIEW)
                .build();
        statementRepository.saveStatement(statement);

        NormalizedTransaction txn1 = NormalizedTransaction.builder()
                .statementId(statementId)
                .amount(500.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("SWIGGY")
                .counterpartyId("swiggy@icici")
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        NormalizedTransaction txn2 = NormalizedTransaction.builder()
                .statementId(statementId)
                .amount(120.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("UBER")
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        transactionRepository.saveTransactions(List.of(txn1, txn2));

        LocalDateTime originalCreatedAt = transactionRepository.findById(txn1.getId()).get().getCreatedAt();
        LocalDateTime originalUpdatedAt = transactionRepository.findById(txn1.getId()).get().getUpdatedAt();

        Thread.sleep(10); // Ensure timestamp advances

        // Act: resolve txn1 with remark and without rule
        reviewService.resolveTransaction(
                txn1.getId(),
                TxnType.PURCHASE,
                TxnCategory.FOOD,
                "Dinner with team",
                null
        );

        // Assert: txn1 is updated
        Optional<NormalizedTransaction> resolvedOpt = transactionRepository.findById(txn1.getId());
        assertThat(resolvedOpt).isPresent();
        NormalizedTransaction resolved = resolvedOpt.get();

        assertThat(resolved.getType()).isEqualTo(TxnType.PURCHASE);
        assertThat(resolved.getCategory()).isEqualTo(TxnCategory.FOOD);
        assertThat(resolved.getReviewRemarks()).isEqualTo("Dinner with team");
        assertThat(resolved.getReviewStatus()).isEqualTo(TxnReviewStatus.MANUAL_REVIEWED);
        assertThat(resolved.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(resolved.getUpdatedAt()).isAfter(originalUpdatedAt);

        // Statement should still be PENDING_REVIEW because txn2 is pending
        assertThat(statementRepository.findById(statementId).get().reviewStatus())
                .isEqualTo(StatementReviewStatus.PENDING_REVIEW);
        assertThat(reviewService.findPendingReviewTransaction(statementId)).hasSize(1);
    }

    @Test
    void resolveTransaction_completesStatement_whenAllTransactionsResolved() {
        UUID statementId = UUID.randomUUID();
        Statement statement = Statement.builder()
                .id(statementId)
                .reviewStatus(StatementReviewStatus.PENDING_REVIEW)
                .build();
        statementRepository.saveStatement(statement);

        NormalizedTransaction txn = NormalizedTransaction.builder()
                .statementId(statementId)
                .amount(100.0)
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();
        transactionRepository.saveTransaction(txn);

        // Act: resolve the only pending transaction
        reviewService.resolveTransaction(
                txn.getId(),
                TxnType.PURCHASE,
                TxnCategory.SHOPPING,
                "Online shopping",
                null
        );

        // Assert: statement flips to COMPLETED
        Statement updatedStatement = statementRepository.findById(statementId).orElseThrow();
        assertThat(updatedStatement.reviewStatus()).isEqualTo(StatementReviewStatus.COMPLETED);
        assertThat(reviewService.findPendingReviewTransaction(statementId)).isEmpty();
    }

    @Test
    void resolveTransaction_savesRuleAndAutoReviewsFutureTransactions() {
        UUID statementId = UUID.randomUUID();
        Statement statement = Statement.builder()
                .id(statementId)
                .reviewStatus(StatementReviewStatus.PENDING_REVIEW)
                .build();
        statementRepository.saveStatement(statement);

        NormalizedTransaction txn = NormalizedTransaction.builder()
                .statementId(statementId)
                .counterparty("SWIGGY")
                .counterpartyId("swiggy@icici")
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();
        transactionRepository.saveTransaction(txn);

        SaveRule saveRule = SaveRule.builder()
                .saveRuleByType(SaveRule.SaveRuleByType.COUNTERPARTY_ID)
                .id("swiggy@icici")
                .autoReview(true)
                .build();

        // Act: resolve txn and save rule
        reviewService.resolveTransaction(
                txn.getId(),
                TxnType.PURCHASE,
                TxnCategory.FOOD,
                "Regular lunch",
                saveRule
        );

        // Assert: rule is saved in repository
        Optional<MappingRule> savedRule = mappingRuleRepository.findByCounterpartyId("swiggy@icici");
        assertThat(savedRule).isPresent();
        assertThat(savedRule.get().txnType()).isEqualTo(TxnType.PURCHASE);
        assertThat(savedRule.get().txnCategory()).isEqualTo(TxnCategory.FOOD);
        assertThat(savedRule.get().autoReview()).isTrue();

        // Verify closed-loop: incoming new transaction from same counterparty auto-reviews!
        NormalizedTransaction incomingTxn = NormalizedTransaction.builder()
                .counterparty("SWIGGY")
                .counterpartyId("swiggy@icici")
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        List<NormalizedTransaction> enriched = transactionMapper.enrich(List.of(incomingTxn));
        assertThat(enriched).hasSize(1);
        NormalizedTransaction autoEnriched = enriched.get(0);

        assertThat(autoEnriched.getType()).isEqualTo(TxnType.PURCHASE);
        assertThat(autoEnriched.getCategory()).isEqualTo(TxnCategory.FOOD);
        assertThat(autoEnriched.getReviewStatus()).isEqualTo(TxnReviewStatus.AUTO_REVIEWED);
    }

    @Test
    void resolveTransaction_whenSaveRuleIsNull_doesNotPersistRule() {
        UUID statementId = UUID.randomUUID();
        Statement statement = Statement.builder()
                .id(statementId)
                .reviewStatus(StatementReviewStatus.PENDING_REVIEW)
                .build();
        statementRepository.saveStatement(statement);

        NormalizedTransaction txn = NormalizedTransaction.builder()
                .statementId(statementId)
                .counterpartyId("random-vpa@upi")
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();
        transactionRepository.saveTransaction(txn);

        // Act: resolve with saveRule = null
        reviewService.resolveTransaction(
                txn.getId(),
                TxnType.PURCHASE,
                TxnCategory.MISCELLANEOUS,
                null,
                null
        );

        // Assert
        assertThat(mappingRuleRepository.findByCounterpartyId("random-vpa@upi")).isEmpty();
        NormalizedTransaction resolved = transactionRepository.findById(txn.getId()).get();
        assertThat(resolved.getReviewStatus()).isEqualTo(TxnReviewStatus.MANUAL_REVIEWED);
    }
}
