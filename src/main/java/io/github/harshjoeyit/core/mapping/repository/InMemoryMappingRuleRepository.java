package io.github.harshjoeyit.core.mapping.repository;

import io.github.harshjoeyit.core.mapping.config.MappingConfig;
import io.github.harshjoeyit.core.mapping.model.MappingRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InMemoryMappingRuleRepository implements MappingRuleRepository {

    public InMemoryMappingRuleRepository(MappingConfig mappingConfig) {
        if (mappingConfig.counterpartyIdRules() != null) {
            log.info("Loaded {}  Counterparty ID rules", mappingConfig.counterpartyIdRules().size());
            idRules.putAll(mappingConfig.counterpartyIdRules());
            log.info("Loaded ID rule keys: {}", idRules.keySet());
        } else {
            log.info("No Counterparty ID rules loaded");
        }
        if (mappingConfig.counterpartyNameRules() != null) {
            log.info("Loaded {}  Counterparty Name rules", mappingConfig.counterpartyNameRules().size());
            nameRules.putAll(mappingConfig.counterpartyNameRules());
            log.info("Loaded Name rule keys: {}", nameRules.keySet());
        } else {
            log.info("No Counterparty Name rules loaded");
        }
    }

    private final Map<String, MappingRule> idRules = new ConcurrentHashMap<>();
    private final Map<String, MappingRule> nameRules = new ConcurrentHashMap<>();

    @Override
    public Optional<MappingRule> findByCounterpartyId(String counterpartyId) {
        return Optional.ofNullable(idRules.get(counterpartyId));
    }

    @Override
    public Optional<MappingRule> findByCounterpartyName(String counterpartyName) {
        return Optional.ofNullable(nameRules.get(counterpartyName));
    }

    @Override
    public void saveByCounterpartyId(String counterpartyId, MappingRule rule) {
        if (idRules.get(counterpartyId) != null) {
            log.error("rule already exists for counterparty id: {}", counterpartyId);
            return;
        }
        // Todo: validate if rule is proper
        idRules.put(counterpartyId, rule);
    }

    @Override
    public void saveByCounterpartyName(String counterpartyName, MappingRule rule) {
        if (nameRules.get(counterpartyName) != null) {
            log.error("rule already exists for counterparty name: {}", counterpartyName);
            return;
        }
        // Todo: validate if rule is proper
        nameRules.put(counterpartyName, rule);
    }
}
