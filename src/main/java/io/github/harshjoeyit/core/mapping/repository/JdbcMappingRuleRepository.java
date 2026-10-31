package io.github.harshjoeyit.core.mapping.repository;

import io.github.harshjoeyit.core.exception.DuplicateMappingRuleException;
import io.github.harshjoeyit.core.mapping.config.MappingConfig;
import io.github.harshjoeyit.core.mapping.model.MappingRule;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.parse.model.TxnType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Repository
@ConditionalOnProperty(name = "app.persistence", havingValue = "sqlite", matchIfMissing = true)
public class JdbcMappingRuleRepository implements MappingRuleRepository {

    private final JdbcClient jdbcClient;
    private final MappingConfig mappingConfig;

    public JdbcMappingRuleRepository(JdbcClient jdbcClient, MappingConfig mappingConfig) {
        this.jdbcClient = jdbcClient;
        this.mappingConfig = mappingConfig;
    }

    @PostConstruct
    public void init() {
        if (mappingConfig == null) {
            log.warn("No mapping config provided");
            return;
        }

        if (mappingConfig.counterpartyIdRules() != null) {
            log.info("Loading {} Counterparty ID rules into database", mappingConfig.counterpartyIdRules().size());
            mappingConfig.counterpartyIdRules().forEach((id, rule) ->
                    insertRuleIfNotExists("COUNTERPARTY_ID", normalize(id), rule)
            );
        } else {
            log.warn("No Counterparty ID rules loaded");
        }

        if (mappingConfig.counterpartyNameRules() != null) {
            log.info("Loading {} Counterparty Name rules into database", mappingConfig.counterpartyNameRules().size());
            mappingConfig.counterpartyNameRules().forEach((name, rule) ->
                    insertRuleIfNotExists("COUNTERPARTY_NAME", normalize(name), rule)
            );
        } else {
            log.warn("No Counterparty Name rules loaded");
        }
    }

    @Override
    public Optional<MappingRule> findByCounterpartyId(String counterpartyId) {
        String key = normalize(counterpartyId);
        if (key.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT * FROM mapping_rules WHERE rule_type = 'COUNTERPARTY_ID' AND rule_key = :key";
        return jdbcClient.sql(sql)
                .param("key", key)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<MappingRule> findByCounterpartyName(String counterpartyName) {
        String key = normalize(counterpartyName);
        if (key.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT * FROM mapping_rules WHERE rule_type = 'COUNTERPARTY_NAME' AND rule_key = :key";
        return jdbcClient.sql(sql)
                .param("key", key)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public void saveByCounterpartyId(String counterpartyId, MappingRule rule, boolean overwrite) {
        String key = normalize(counterpartyId);
        if (key.isBlank()) {
            log.warn("Cannot save rule with null or blank counterparty ID");
            return;
        }

        if (!overwrite && existsRule("COUNTERPARTY_ID", key)) {
            log.error("rule already exists for counterparty id: {}", counterpartyId);
            throw new DuplicateMappingRuleException("Rule already exists for counterparty ID: " + counterpartyId);
        }

        upsertRule("COUNTERPARTY_ID", key, rule);
    }

    @Override
    public void saveByCounterpartyName(String counterpartyName, MappingRule rule, boolean overwrite) {
        String key = normalize(counterpartyName);
        if (key.isBlank()) {
            log.warn("Cannot save rule with null or blank counterparty name");
            return;
        }

        if (!overwrite && existsRule("COUNTERPARTY_NAME", key)) {
            log.error("rule already exists for counterparty name: {}", counterpartyName);
            throw new DuplicateMappingRuleException("Rule already exists for counterparty name: " + counterpartyName);
        }

        upsertRule("COUNTERPARTY_NAME", key, rule);
    }

    private boolean existsRule(String ruleType, String normalizedKey) {
        String sql = "SELECT COUNT(*) FROM mapping_rules WHERE rule_type = :ruleType AND rule_key = :ruleKey";
        Integer count = jdbcClient.sql(sql)
                .param("ruleType", ruleType)
                .param("ruleKey", normalizedKey)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private void insertRuleIfNotExists(String ruleType, String normalizedKey, MappingRule rule) {
        if (normalizedKey.isBlank() || rule == null) {
            return;
        }

        String sql = """
                INSERT INTO mapping_rules (
                    rule_type, rule_key, category, type, auto_review, created_at, updated_at
                ) VALUES (
                    :ruleType, :ruleKey, :category, :type, :autoReview, :createdAt, :updatedAt
                )
                ON CONFLICT(rule_type, rule_key) DO NOTHING
                """;

        String now = LocalDateTime.now().toString();
        jdbcClient.sql(sql)
                .param("ruleType", ruleType)
                .param("ruleKey", normalizedKey)
                .param("category", rule.txnCategory() != null ? rule.txnCategory().name() : null)
                .param("type", rule.txnType() != null ? rule.txnType().name() : null)
                .param("autoReview", rule.autoReview() ? 1 : 0)
                .param("createdAt", now)
                .param("updatedAt", now)
                .update();
    }

    private void upsertRule(String ruleType, String normalizedKey, MappingRule rule) {
        String sql = """
                INSERT INTO mapping_rules (
                    rule_type, rule_key, category, type, auto_review, created_at, updated_at
                ) VALUES (
                    :ruleType, :ruleKey, :category, :type, :autoReview, :createdAt, :updatedAt
                )
                ON CONFLICT(rule_type, rule_key) DO UPDATE SET
                    category    = excluded.category,
                    type        = excluded.type,
                    auto_review = excluded.auto_review,
                    updated_at  = excluded.updated_at
                """;

        String now = LocalDateTime.now().toString();
        jdbcClient.sql(sql)
                .param("ruleType", ruleType)
                .param("ruleKey", normalizedKey)
                .param("category", rule.txnCategory() != null ? rule.txnCategory().name() : null)
                .param("type", rule.txnType() != null ? rule.txnType().name() : null)
                .param("autoReview", rule.autoReview() ? 1 : 0)
                .param("createdAt", now)
                .param("updatedAt", now)
                .update();
        log.debug("Saved {} rule for key: {}", ruleType, normalizedKey);
    }

    // Custom mapping logic as mappingRule POJO is not same as the table mapping_rules
    private MappingRule mapRow(ResultSet rs, int rowNum) throws SQLException {
        String categoryStr = rs.getString("category");
        String typeStr = rs.getString("type");

        TxnCategory category = (categoryStr != null && !categoryStr.isBlank())
                ? TxnCategory.valueOf(categoryStr)
                : null;
        TxnType type = (typeStr != null && !typeStr.isBlank())
                ? TxnType.valueOf(typeStr)
                : null;
        boolean autoReview = rs.getInt("auto_review") == 1;

        return MappingRule.builder()
                .txnCategory(category)
                .txnType(type)
                .autoReview(autoReview)
                .build();
    }

    private String normalize(String key) {
        if (key == null) {
            return "";
        }
        // Trim surrounding spaces, collapse consecutive internal spaces, convert to uppercase
        return key.trim().replaceAll("\\s+", " ").toUpperCase();
    }
}
