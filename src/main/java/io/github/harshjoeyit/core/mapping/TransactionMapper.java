package io.github.harshjoeyit.core.mapping;

import io.github.harshjoeyit.core.mapping.model.MappingRule;
import io.github.harshjoeyit.core.mapping.repository.MappingRuleRepository;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class TransactionMapper {

    private final MappingRuleRepository mappingRuleRepository;

    public TransactionMapper(MappingRuleRepository mappingRuleRepository) {
        this.mappingRuleRepository = mappingRuleRepository;
    }

    public List<NormalizedTransaction> enrich(List<NormalizedTransaction> unmappedTransactions) {
        log.info("starting mapping for {}", unmappedTransactions.size());
        List<NormalizedTransaction> mappedTransactions = new ArrayList<>(unmappedTransactions.size());

        for (NormalizedTransaction utxn : unmappedTransactions) {
            mappedTransactions.add(
                    findRule(utxn)
                            .map(rule -> applyRule(utxn, rule))
                            .orElse(utxn)
            );
        }

        log.info("Mapped {} transactions", mappedTransactions.size());
        return mappedTransactions;
    }

    private Optional<MappingRule> findRule(NormalizedTransaction txn) {
        // Try Counterparty ID first
        if (txn.getCounterpartyId() != null && !txn.getCounterpartyId().isBlank()) {
            Optional<MappingRule> rule = mappingRuleRepository.findByCounterpartyId(txn.getCounterpartyId());
            if (rule.isPresent()) {
                return rule;
            }
        }
        // Fall back to Counterparty Name
        if (txn.getCounterparty() != null && !txn.getCounterparty().isBlank()) {
            return mappingRuleRepository.findByCounterpartyName(txn.getCounterparty());
        }
        return Optional.empty();
    }

    private NormalizedTransaction applyRule(NormalizedTransaction unmappedTxn, MappingRule rule) {
        NormalizedTransaction mappedTxn =  unmappedTxn.toBuilder().build();
        mappedTxn.setCategory(rule.txnCategory());
        mappedTxn.setType(rule.txnType());
        if (rule.autoReview()) {
            mappedTxn.setReviewStatus(TxnReviewStatus.AUTO_REVIEWED);
        }
        return mappedTxn;
    }
}
