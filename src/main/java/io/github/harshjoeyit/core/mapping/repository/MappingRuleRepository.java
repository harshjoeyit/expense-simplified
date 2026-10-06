package io.github.harshjoeyit.core.mapping.repository;

import io.github.harshjoeyit.core.mapping.model.MappingRule;

import java.util.Optional;

public interface MappingRuleRepository {
    Optional<MappingRule> findByCounterpartyId(String counterpartyId);
    Optional<MappingRule> findByCounterpartyName(String counterpartyName);

    void saveByCounterpartyId(String counterpartyId, MappingRule rule);
    void saveByCounterpartyName(String counterpartyName,  MappingRule rule);
}
