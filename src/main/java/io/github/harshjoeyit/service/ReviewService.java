package io.github.harshjoeyit.service;

import io.github.harshjoeyit.core.exception.InvalidMappingRule;
import io.github.harshjoeyit.core.exception.InvalidStatementId;
import io.github.harshjoeyit.core.exception.InvalidTransactionId;
import io.github.harshjoeyit.core.mapping.model.MappingRule;
import io.github.harshjoeyit.core.mapping.repository.MappingRuleRepository;
import io.github.harshjoeyit.core.model.*;
import io.github.harshjoeyit.core.parse.model.TxnType;
import io.github.harshjoeyit.core.repository.StatementRepository;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ReviewService {

    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final MappingRuleRepository mappingRuleRepository;

    public ReviewService (StatementRepository statementRepository,
                          TransactionRepository transactionRepository,
                          MappingRuleRepository mappingRuleRepository) {
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.mappingRuleRepository = mappingRuleRepository;
    }

    public List<NormalizedTransaction> findPendingReviewTransaction(UUID statementId) throws NoSuchElementException {
        Optional<Statement> statement;
        // find statement first and then find transactions
        if (statementId == null) {
            // find the transactions for latest uploaded statement
            log.info("finding the latest statement...");
            statement = statementRepository.findLatest();
        } else {
            // find the transactions for provided statementId
            log.info("finding the latest statement: {}", statementId);
            statement = statementRepository.findById(statementId);
        }

        log.debug("found statement: {}", statement);

        Statement st = statement
                .orElseThrow(() -> new NoSuchElementException("Statement not found"));

        return transactionRepository.findPendingReviewTransactionsByStatementId(st.id());
    }

    // Updates the transaction with provided inputs
    public void resolveTransaction(UUID txnId, TxnType txnType, TxnCategory txnCategory, String reviewRemark,
                                   SaveRule saveRule) throws InvalidTransactionId, InvalidStatementId, UnsupportedOperationException {
        if (txnId == null) {
            throw new InvalidTransactionId("transaction Id cannot be null");
        }

        NormalizedTransaction transaction = transactionRepository
                .findById(txnId)
                .orElseThrow(() -> new InvalidTransactionId("transaction" + txnId + " not found"));

        if (txnCategory != null && txnCategory != transaction.getCategory()) {
            log.debug("updating category from {} to {}", transaction.getCategory(), txnCategory);
            transaction.setCategory(txnCategory);
        }
        if (txnType != null && txnType != transaction.getType()) {
            log.debug("updating type from {} to {}", transaction.getType(), txnType);
            transaction.setType(txnType);
        }
        if (reviewRemark != null && !reviewRemark.isBlank()) {
         log.debug("adding review remarks: {}", reviewRemark);
         transaction.setReviewRemarks(reviewRemark);
        }
        transaction.setReviewStatus(TxnReviewStatus.MANUAL_REVIEWED);

        // Save updated transaction
        transactionRepository.saveTransaction(transaction);

        // Save rule
        if (saveRule != null) {
            saveRule(saveRule, transaction.getType(), transaction.getCategory());
        }

        // Mark statement as review completed when no transaction remain to be reviewed
        if (transactionRepository.findPendingReviewTransactionsByStatementId(
             transaction.getStatementId()).isEmpty()) {
            markStatementAsReviewComplete(transaction.getStatementId());
        }
    }

    // Save rule to mapping repository
    public void saveRule(SaveRule saveRule, TxnType txnType, TxnCategory txnCategory) throws UnsupportedOperationException {
        saveRule(saveRule, txnType, txnCategory, false);
    }

    public void saveRule(SaveRule saveRule, TxnType txnType, TxnCategory txnCategory, boolean overwrite) throws UnsupportedOperationException {

        if (saveRule == null) {
            log.error("Save rule cannot be null");
            throw new InvalidMappingRule("Save rule cannot be null");
        }

        if (saveRule.getSaveRuleByType() == null || saveRule.getId() == null || saveRule.getId().isBlank()) {
            log.error("Save rule is incomplete. Type and ID are required: {}", saveRule);
            throw new InvalidMappingRule("Save rule is incomplete. Type and ID are required:" + saveRule);
        }

        MappingRule mappingRule = MappingRule.builder()
                .txnType(txnType)
                .txnCategory(txnCategory)
                .autoReview(saveRule.isAutoReview())
                .build();

        switch (saveRule.getSaveRuleByType()) {
            case COUNTERPARTY_ID -> mappingRuleRepository.saveByCounterpartyId(saveRule.getId(), mappingRule, overwrite);
            case COUNTERPARTY_NAME -> mappingRuleRepository.saveByCounterpartyName(saveRule.getId(), mappingRule, overwrite);
            default -> throw new UnsupportedOperationException(
                    "Unsupported rule type" + saveRule.getSaveRuleByType()
            );
        }
    }

    // Save statement with review status as completed
    private void markStatementAsReviewComplete(UUID statementId) throws InvalidStatementId {
        Statement statement = statementRepository.findById(statementId).orElseThrow(
                () -> new InvalidStatementId("Invalid Id: " + statementId)
        );
        statementRepository.saveStatement(statement.withMarkComplete());
    }

    public record ExistingRuleMatch(
            SaveRule.SaveRuleByType matchType,
            String matchKey,
            MappingRule rule
    ) {}

    public Optional<ExistingRuleMatch> findExistingRule(String counterpartyId, String counterpartyName) {
        if (counterpartyId != null && !counterpartyId.isBlank()) {
            Optional<MappingRule> rule = mappingRuleRepository.findByCounterpartyId(counterpartyId);
            if (rule.isPresent()) {
                return Optional.of(new ExistingRuleMatch(
                        SaveRule.SaveRuleByType.COUNTERPARTY_ID,
                        counterpartyId,
                        rule.get()
                ));
            }
        }
        if (counterpartyName != null && !counterpartyName.isBlank()) {
            Optional<MappingRule> rule = mappingRuleRepository.findByCounterpartyName(counterpartyName);
            if (rule.isPresent()) {
                return Optional.of(new ExistingRuleMatch(
                        SaveRule.SaveRuleByType.COUNTERPARTY_NAME,
                        counterpartyName,
                        rule.get()
                ));
            }
        }
        return Optional.empty();
    }

    public Optional<ExistingRuleMatch> findExistingRule(NormalizedTransaction txn) {
        return txn != null
                ? findExistingRule(txn.getCounterpartyId(), txn.getCounterparty())
                : Optional.empty();
    }
}

