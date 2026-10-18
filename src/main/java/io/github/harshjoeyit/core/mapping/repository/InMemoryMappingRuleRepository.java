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

    private final Map<String, MappingRule> idRules = new ConcurrentHashMap<>();
    private final Map<String, MappingRule> nameRules = new ConcurrentHashMap<>();

    public InMemoryMappingRuleRepository(MappingConfig mappingConfig) {
        if (mappingConfig == null) {
            log.warn("No mapping config");
            return;
        }

        if (mappingConfig.counterpartyIdRules() != null) {
            log.info("Loaded {}  Counterparty ID rules", mappingConfig.counterpartyIdRules().size());
            mappingConfig.counterpartyIdRules().forEach(
                    (id, rule) -> idRules.put(normalize(id), rule)
            );
        } else {
            log.warn("No Counterparty ID rules loaded");
        }

        if (mappingConfig.counterpartyNameRules() != null) {
            log.info("Loaded {}  Counterparty Name rules", mappingConfig.counterpartyNameRules().size());
            mappingConfig.counterpartyNameRules().forEach(
                    (id, rule) -> nameRules.put(normalize(id), rule)
            );
        } else {
            log.warn("No Counterparty Name rules loaded");
        }
    }

    @Override
    public Optional<MappingRule> findByCounterpartyId(String counterpartyId) {
        return Optional.ofNullable(idRules.get(normalize(counterpartyId)));
    }

    @Override
    public Optional<MappingRule> findByCounterpartyName(String counterpartyName) {
        return Optional.ofNullable(nameRules.get(normalize(counterpartyName)));
    }

    @Override
    public void saveByCounterpartyId(String counterpartyId, MappingRule rule) {
        String cpId = normalize(counterpartyId);
        if (idRules.get(cpId) != null) {
            log.error("rule already exists for counterparty id: {}", counterpartyId);
            return;
        }
        // Todo: validate if rule is proper
        idRules.put(cpId, rule);
    }

    @Override
    public void saveByCounterpartyName(String counterpartyName, MappingRule rule) {
        String cpName = normalize(counterpartyName);
        if (nameRules.get(cpName) != null) {
            log.error("rule already exists for counterparty name: {}", counterpartyName);
            return;
        }
        // Todo: validate if rule is proper
        nameRules.put(cpName, rule);
    }

    private String normalize(String key) {
        if (key == null) {
            return "";
        }
        // Trim surrounding spaces, collapse consecutive internal spaces, convert to uppercase
        return key.trim().replaceAll("\\s+", " ").toUpperCase();
    }
}
