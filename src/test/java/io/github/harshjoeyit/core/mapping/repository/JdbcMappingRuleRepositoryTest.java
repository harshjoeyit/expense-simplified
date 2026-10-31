package io.github.harshjoeyit.core.mapping.repository;

import io.github.harshjoeyit.core.exception.DuplicateMappingRuleException;
import io.github.harshjoeyit.core.mapping.config.MappingConfig;
import io.github.harshjoeyit.core.mapping.model.MappingRule;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.parse.model.TxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.persistence=sqlite",
        "spring.shell.interactive.enabled=false",
        "spring.datasource.url=jdbc:sqlite:target/test-expense-simplified.db?journal_mode=WAL&foreign_keys=ON"
})
@TestExecutionListeners(listeners = {
        DependencyInjectionTestExecutionListener.class
})
class JdbcMappingRuleRepositoryTest {

    @Autowired
    private JdbcMappingRuleRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM mapping_rules").update();
    }

    @Test
    void findByCounterpartyId_withCaseAndWhitespaceDifferences_normalizesAndFindsRule() {
        MappingRule idRule = MappingRule.builder()
                .txnCategory(TxnCategory.UTILITIES)
                .txnType(TxnType.PURCHASE)
                .autoReview(true)
                .build();

        repository.saveByCounterpartyId("  8618514430-2@axl  ", idRule);

        Optional<MappingRule> match1 = repository.findByCounterpartyId("8618514430-2@AXL");
        assertThat(match1).isPresent().contains(idRule);

        Optional<MappingRule> match2 = repository.findByCounterpartyId("   8618514430-2@axl ");
        assertThat(match2).isPresent().contains(idRule);
    }

    @Test
    void findByCounterpartyName_withCaseAndInternalWhitespace_normalizesAndFindsRule() {
        MappingRule nameRule = MappingRule.builder()
                .txnCategory(TxnCategory.DINE_OUT_OR_ORDER)
                .txnType(TxnType.PURCHASE)
                .autoReview(false)
                .build();

        repository.saveByCounterpartyName("Swiggy Bangalore", nameRule);

        Optional<MappingRule> match1 = repository.findByCounterpartyName("SWIGGY BANGALORE");
        assertThat(match1).isPresent().contains(nameRule);

        Optional<MappingRule> match2 = repository.findByCounterpartyName("  swiggy    bangalore  ");
        assertThat(match2).isPresent().contains(nameRule);
    }

    @Test
    void findMethods_withNullOrBlank_returnsEmptyOptionalWithoutException() {
        assertThat(repository.findByCounterpartyId(null)).isEmpty();
        assertThat(repository.findByCounterpartyId("   ")).isEmpty();
        assertThat(repository.findByCounterpartyName(null)).isEmpty();
        assertThat(repository.findByCounterpartyName("")).isEmpty();
    }

    @Test
    void saveByCounterpartyId_whenDuplicateWithoutOverwrite_throwsDuplicateMappingRuleException() {
        MappingRule rule1 = MappingRule.builder().txnType(TxnType.PURCHASE).txnCategory(TxnCategory.FOOD).build();
        MappingRule rule2 = MappingRule.builder().txnType(TxnType.PURCHASE).txnCategory(TxnCategory.SHOPPING).build();

        repository.saveByCounterpartyId("upi@okaxis", rule1);

        assertThatThrownBy(() -> repository.saveByCounterpartyId("upi@okaxis", rule2))
                .isInstanceOf(DuplicateMappingRuleException.class)
                .hasMessageContaining("Rule already exists for counterparty ID: upi@okaxis");

        assertThatThrownBy(() -> repository.saveByCounterpartyId("upi@okaxis", rule2, false))
                .isInstanceOf(DuplicateMappingRuleException.class);
    }

    @Test
    void saveByCounterpartyId_whenDuplicateWithOverwrite_updatesExistingRule() {
        MappingRule rule1 = MappingRule.builder().txnType(TxnType.PURCHASE).txnCategory(TxnCategory.FOOD).build();
        MappingRule rule2 = MappingRule.builder().txnType(TxnType.PURCHASE).txnCategory(TxnCategory.SHOPPING).build();

        repository.saveByCounterpartyId("upi@okaxis", rule1);
        repository.saveByCounterpartyId("upi@okaxis", rule2, true);

        Optional<MappingRule> updated = repository.findByCounterpartyId("upi@okaxis");
        assertThat(updated).isPresent().contains(rule2);
    }

    @Test
    void saveByCounterpartyName_whenDuplicateWithoutOverwrite_throwsDuplicateMappingRuleException() {
        MappingRule rule1 = MappingRule.builder().txnType(TxnType.PURCHASE).txnCategory(TxnCategory.FOOD).build();
        MappingRule rule2 = MappingRule.builder().txnType(TxnType.PURCHASE).txnCategory(TxnCategory.SHOPPING).build();

        repository.saveByCounterpartyName("Amazon", rule1);

        assertThatThrownBy(() -> repository.saveByCounterpartyName("Amazon", rule2))
                .isInstanceOf(DuplicateMappingRuleException.class)
                .hasMessageContaining("Rule already exists for counterparty name: Amazon");

        assertThatThrownBy(() -> repository.saveByCounterpartyName("Amazon", rule2, false))
                .isInstanceOf(DuplicateMappingRuleException.class);
    }

    @Test
    void saveByCounterpartyName_whenDuplicateWithOverwrite_updatesExistingRule() {
        MappingRule rule1 = MappingRule.builder().txnType(TxnType.PURCHASE).txnCategory(TxnCategory.FOOD).build();
        MappingRule rule2 = MappingRule.builder().txnType(TxnType.PURCHASE).txnCategory(TxnCategory.SHOPPING).build();

        repository.saveByCounterpartyName("Amazon", rule1);
        repository.saveByCounterpartyName("Amazon", rule2, true);

        Optional<MappingRule> updated = repository.findByCounterpartyName("Amazon");
        assertThat(updated).isPresent().contains(rule2);
    }

    @Test
    void saveAndFind_withNullCategoryOrType_persistsAndRetrievesCorrectly() {
        MappingRule ruleWithoutCategory = MappingRule.builder()
                .txnType(TxnType.SELF_ACCOUNT_TRANSFER)
                .autoReview(true)
                .build();

        repository.saveByCounterpartyId("self@ybl", ruleWithoutCategory);

        Optional<MappingRule> found = repository.findByCounterpartyId("self@ybl");
        assertThat(found).isPresent();
        assertThat(found.get().txnType()).isEqualTo(TxnType.SELF_ACCOUNT_TRANSFER);
        assertThat(found.get().txnCategory()).isNull();
        assertThat(found.get().autoReview()).isTrue();
    }

    @Test
    void init_seedsFromMappingConfig_withoutOverwritingExistingEdits() {
        MappingRule seedRule = MappingRule.builder()
                .txnType(TxnType.PURCHASE)
                .txnCategory(TxnCategory.FOOD)
                .autoReview(false)
                .build();

        MappingConfig customConfig = new MappingConfig(
                Map.of("test@upi", seedRule),
                Map.of("Test Merchant", seedRule)
        );

        JdbcMappingRuleRepository customRepo = new JdbcMappingRuleRepository(jdbcClient, customConfig);
        customRepo.init();

        Optional<MappingRule> seededId = customRepo.findByCounterpartyId("test@upi");
        assertThat(seededId).isPresent().contains(seedRule);

        Optional<MappingRule> seededName = customRepo.findByCounterpartyName("Test Merchant");
        assertThat(seededName).isPresent().contains(seedRule);

        // User updates rule in DB
        MappingRule userModifiedRule = MappingRule.builder()
                .txnType(TxnType.PURCHASE)
                .txnCategory(TxnCategory.SHOPPING)
                .autoReview(true)
                .build();
        customRepo.saveByCounterpartyId("test@upi", userModifiedRule, true);

        // Re-run init() simulating app restart
        customRepo.init();

        // Must preserve the user modification
        Optional<MappingRule> afterRestart = customRepo.findByCounterpartyId("test@upi");
        assertThat(afterRestart).isPresent().contains(userModifiedRule);
    }

    @Test
    void init_restartsDoNotAdvanceSqliteSequence() {
        MappingRule seedRule = MappingRule.builder()
                .txnType(TxnType.PURCHASE)
                .txnCategory(TxnCategory.FOOD)
                .autoReview(false)
                .build();

        MappingConfig customConfig = new MappingConfig(
                Map.of("test@upi", seedRule),
                Map.of("Test Merchant", seedRule)
        );

        JdbcMappingRuleRepository customRepo = new JdbcMappingRuleRepository(jdbcClient, customConfig);
        customRepo.init();

        Integer seqAfterFirstInit = jdbcClient.sql("SELECT seq FROM sqlite_sequence WHERE name = 'mapping_rules'")
                .query(Integer.class)
                .single();

        // Re-run init multiple times simulating app restarts
        customRepo.init();
        customRepo.init();

        Integer seqAfterSubsequentInits = jdbcClient.sql("SELECT seq FROM sqlite_sequence WHERE name = 'mapping_rules'")
                .query(Integer.class)
                .single();

        assertThat(seqAfterSubsequentInits).isEqualTo(seqAfterFirstInit);
    }
}
