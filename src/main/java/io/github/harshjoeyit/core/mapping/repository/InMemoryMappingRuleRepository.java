package io.github.harshjoeyit.core.mapping.repository;

import io.github.harshjoeyit.core.mapping.model.MappingRule;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.parse.model.TxnType;
import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    public void init() {
        Map<String, MappingRule> nameRulesData = Map.of(
                "Amazon Seller Services BANGALORE",
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(null)
                        .autoReview(false)
                        .build(),
                "AMAZON PAY INDIA PRIVA Bangalore",
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(null)
                        .autoReview(false)
                        .build(),
                "WWW SWIGGY IN BANGALORE",
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.DINE_OUT_OR_ORDER)
                        .autoReview(false)
                        .build(),
                "Gayitri",                          // Purva season fruits
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.FOOD)
                        .autoReview(true)
                        .build(),
                "Elior India Catering Llp",         // Office food vendor
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.FOOD)
                        .autoReview(true)
                        .build(),
                "Elior India Food Services Llp",   // Office food vendor
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.FOOD)
                        .autoReview(true)
                        .build(),
                "Blinkit",
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.UTILITIES)
                        .autoReview(false)
                        .build(),
                "M K Retail Company",
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.FOOD)
                        .autoReview(false)
                        .build(),
                "Airtel",
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.UTILITIES)
                        .autoReview(true)
                        .build(),
                "Kunhabdulla",
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.FOOD)
                        .autoReview(true)
                        .build()
        );

        nameRules.putAll(nameRulesData);

        nameRules.put("Myjio In", MappingRule.builder()
                .txnType(TxnType.PURCHASE)
                .txnCategory(TxnCategory.UTILITIES)
                .autoReview(true)
                .build());
        nameRules.put("ixigo", MappingRule.builder()
                .txnType(TxnType.PURCHASE)
                .txnCategory(TxnCategory.FLIGHT)
                .autoReview(true)
                .build());
        nameRules.put("MYNTRA DESIGNS PRIVATE Bangalore", MappingRule.builder()
                .txnType(TxnType.PURCHASE)
                .txnCategory(TxnCategory.SHOPPING)
                .autoReview(false)
                .build());
        nameRules.put("Cashfree*Myntra BANGALORE", MappingRule.builder()
                .txnType(TxnType.PURCHASE)
                .txnCategory(TxnCategory.SHOPPING)
                .autoReview(false)
                .build());
        nameRules.put("RSP*INSTAMART BANGALORE ", MappingRule.builder()
                .txnType(TxnType.PURCHASE)
                .txnCategory(TxnCategory.FOOD)
                .autoReview(false)
                .build());
        nameRules.put("ZOMATO LTD BANGALORE", MappingRule.builder()
                .txnType(TxnType.PURCHASE)
                .txnCategory(TxnCategory.DINE_OUT_OR_ORDER)
                .autoReview(true)
                .build());
        nameRules.put("PTM*ZOMATO LIMITED NEW DELHI", MappingRule.builder()
                .txnType(TxnType.PURCHASE)
                .txnCategory(TxnCategory.DINE_OUT_OR_ORDER)
                .autoReview(true)
                .build());

        Map<String, MappingRule> idRulesData = Map.of(
                "HARSHITGANGWAR02@YBL",
                MappingRule.builder()
                        .txnType(TxnType.SELF_ACCOUNT_TRANSFER)
                        .txnCategory(TxnCategory.SELF)
                        .autoReview(true)
                        .build(),
                "BHARATPE.8B0L1S1U9W89603@FBPE",    // Sports vault badminton
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.FUN_AND_PLAY)
                        .autoReview(true)
                        .build(),
                "8310661553-2@YBL",                 // Gayitri - Purva seasons fruits
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.FOOD)
                        .autoReview(true)
                        .build(),
                "PAYTM.S20YOPY@PTY",                // Krishna murthy - fruits/veggies Byrasandra
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.FOOD)
                        .autoReview(true)
                        .build(),
                "8618514430-2@AXL",                // Press anna Byrasandra
                MappingRule.builder()
                        .txnType(TxnType.PURCHASE)
                        .txnCategory(TxnCategory.UTILITIES)
                        .autoReview(true)
                        .build()
        );

        idRules.putAll(idRulesData);

        log.info("Added {} ID rules", idRules.size());
        log.info("Added {} name rules", nameRules.size());
    }

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
