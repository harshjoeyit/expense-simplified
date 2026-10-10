package io.github.harshjoeyit.core.mapping.repository;

import io.github.harshjoeyit.core.mapping.config.MappingConfig;
import io.github.harshjoeyit.core.mapping.model.MappingRule;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.parse.model.TxnType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMappingRuleRepositoryTest {

    @Test
    void findByCounterpartyId_withCaseAndWhitespaceDifferences_normalizesAndFindsRule() {
        // Arrange: Config has lowercase with extra whitespace
        MappingRule idRule = MappingRule.builder()
                .txnCategory(TxnCategory.UTILITIES)
                .txnType(TxnType.PURCHASE)
                .autoReview(true)
                .build();

        MappingConfig config = new MappingConfig(
                Map.of("  8618514430-2@axl  ", idRule),
                Map.of()
        );

        InMemoryMappingRuleRepository repository = new InMemoryMappingRuleRepository(config);

        // Act & Assert 1: Query with uppercase and no spaces
        Optional<MappingRule> match1 = repository.findByCounterpartyId("8618514430-2@AXL");
        assertThat(match1).isPresent().contains(idRule);

        // Act & Assert 2: Query with lowercase and different spacing
        Optional<MappingRule> match2 = repository.findByCounterpartyId("   8618514430-2@axl ");
        assertThat(match2).isPresent().contains(idRule);
    }

    @Test
    void findByCounterpartyName_withCaseAndInternalWhitespace_normalizesAndFindsRule() {
        // Arrange: Config has mixed case "Swiggy Bangalore"
        MappingRule nameRule = MappingRule.builder()
                .txnCategory(TxnCategory.DINE_OUT_OR_ORDER)
                .txnType(TxnType.PURCHASE)
                .autoReview(false)
                .build();

        MappingConfig config = new MappingConfig(
                Map.of(),
                Map.of("Swiggy Bangalore", nameRule)
        );

        InMemoryMappingRuleRepository repository = new InMemoryMappingRuleRepository(config);

        // Act & Assert 1: Query with uppercase
        Optional<MappingRule> match1 = repository.findByCounterpartyName("SWIGGY BANGALORE");
        assertThat(match1).isPresent().contains(nameRule);

        // Act & Assert 2: Query with lowercase and multiple internal spaces (common in bank narrations)
        Optional<MappingRule> match2 = repository.findByCounterpartyName("  swiggy    bangalore  ");
        assertThat(match2).isPresent().contains(nameRule);
    }

    @Test
    void saveAndFind_normalizesBothOnWriteAndOnRead() {
        InMemoryMappingRuleRepository repository = new InMemoryMappingRuleRepository(
                new MappingConfig(Map.of(), Map.of())
        );

        MappingRule rule = MappingRule.builder()
                .txnCategory(TxnCategory.FOOD)
                .txnType(TxnType.PURCHASE)
                .autoReview(true)
                .build();

        // Save with lowercase and leading/trailing spaces
        repository.saveByCounterpartyName("  gayitri  ", rule);

        // Lookup with uppercase and clean string
        Optional<MappingRule> found = repository.findByCounterpartyName("GAYITRI");
        assertThat(found).isPresent().contains(rule);
    }

    @Test
    void findMethods_withNullOrBlank_returnsEmptyOptionalWithoutException() {
        InMemoryMappingRuleRepository repository = new InMemoryMappingRuleRepository(
                new MappingConfig(Map.of(), Map.of())
        );

        assertThat(repository.findByCounterpartyId(null)).isEmpty();
        assertThat(repository.findByCounterpartyId("   ")).isEmpty();
        assertThat(repository.findByCounterpartyName(null)).isEmpty();
        assertThat(repository.findByCounterpartyName("")).isEmpty();
    }
}
