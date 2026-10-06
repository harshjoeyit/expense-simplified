package io.github.harshjoeyit.core.mapping;

import io.github.harshjoeyit.core.mapping.model.MappingRule;
import io.github.harshjoeyit.core.mapping.repository.MappingRuleRepository;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

        for(NormalizedTransaction utxn : unmappedTransactions) {
            if (utxn.getCounterpartyId() != null && !utxn.getCounterpartyId().isEmpty()) {
                Optional<MappingRule> rule = mappingRuleRepository.findByCounterpartyId(utxn.getCounterpartyId());
                rule.ifPresentOrElse(
                        mappingRule -> mappedTransactions.add(applyRule(utxn, mappingRule)),
                        () -> mappedTransactions.add(utxn));
            } else if (utxn.getCounterparty() != null && !utxn.getCounterparty().isEmpty()) {
                Optional<MappingRule> rule = mappingRuleRepository.findByCounterpartyName(utxn.getCounterparty());
                rule.ifPresentOrElse(
                        mappingRule -> mappedTransactions.add(applyRule(utxn, mappingRule)),
                        () -> mappedTransactions.add(utxn));
            } else {
                log.info("No mapping rule found for Txn: {} ", utxn);
                mappedTransactions.add(utxn);
            }
        }

        log.info("Mapped {} transactions", mappedTransactions.size());

        return mappedTransactions;
    }

    private NormalizedTransaction applyRule(NormalizedTransaction unmappedTxn, MappingRule rule) {
        NormalizedTransaction mappedTxn =  unmappedTxn.toBuilder().build();
        mappedTxn.setCategory(rule.getTxnCategory());
        mappedTxn.setType(rule.getTxnType());
        if (rule.isAutoReview()) {
            mappedTxn.setReviewStatus(TxnReviewStatus.AUTO_REVIEWED);
        }
        return mappedTxn;
    }
}
