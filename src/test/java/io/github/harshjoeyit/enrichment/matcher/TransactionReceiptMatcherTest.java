package io.github.harshjoeyit.enrichment.matcher;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.enrichment.model.OrderReceipt;
import io.github.harshjoeyit.enrichment.model.OrderReceiptItem;
import io.github.harshjoeyit.enrichment.model.TransactionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionReceiptMatcherTest {

    private TransactionReceiptMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new TransactionReceiptMatcher(Map.of(
                "SWIGGY", List.of("SWIGGY", "INSTAMART"),
                "AMAZON", List.of("AMAZON", "AMZN"),
                "FLIPKART", List.of("FLIPKART", "FKART")
        ));
    }

    @Test
    @DisplayName("Exact amount and counterparty within date window matches transaction")
    void match_exactAmountAndPlatform_matches() {
        UUID txnId = UUID.randomUUID();
        LocalDateTime txnTime = LocalDateTime.of(2026, 10, 15, 14, 0);

        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(320.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("SWIGGY")
                .datetime(txnTime)
                .build();

        OrderReceipt receipt = OrderReceipt.builder()
                .platform("SWIGGY")
                .platformOrderId("18392104")
                .outletName("Truffles")
                .orderDateTime(txnTime.minusMinutes(30))
                .totalAmount(320.0)
                .items(List.of(
                        OrderReceiptItem.builder().itemName("Burger").quantity(1).totalPrice(240.0).build(),
                        OrderReceiptItem.builder().itemName("Fries").quantity(1).totalPrice(80.0).build()
                ))
                .build();

        Map<UUID, List<TransactionItem>> matched = matcher.match(List.of(txn), List.of(receipt));

        assertThat(matched).containsOnlyKeys(txnId);
        List<TransactionItem> items = matched.get(txnId);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).itemName()).isEqualTo("Burger");
        assertThat(items.get(0).platform()).isEqualTo("SWIGGY");
        assertThat(items.get(0).outletName()).isEqualTo("Truffles");
        assertThat(items.get(1).itemName()).isEqualTo("Fries");
    }

    @Test
    @DisplayName("Amount mismatch prevents matching")
    void match_amountMismatch_doesNotMatch() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(320.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("SWIGGY")
                .datetime(LocalDateTime.now())
                .build();

        OrderReceipt receipt = OrderReceipt.builder()
                .platform("SWIGGY")
                .totalAmount(350.0) // Different amount
                .orderDateTime(LocalDateTime.now())
                .items(List.of(OrderReceiptItem.builder().itemName("Burger").totalPrice(350.0).build()))
                .build();

        Map<UUID, List<TransactionItem>> matched = matcher.match(List.of(txn), List.of(receipt));
        assertThat(matched).isEmpty();
    }

    @Test
    @DisplayName("1-to-1 Invariant: Two identical txns with one receipt matches only one")
    void match_identicalTxns_matchesOnlyOneReceipt() {
        UUID txn1 = UUID.randomUUID();
        UUID txn2 = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 10, 15, 14, 0);

        NormalizedTransaction t1 = NormalizedTransaction.builder()
                .id(txn1)
                .amount(320.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("SWIGGY")
                .datetime(now)
                .build();

        NormalizedTransaction t2 = NormalizedTransaction.builder()
                .id(txn2)
                .amount(320.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("SWIGGY")
                .datetime(now.plusHours(4))
                .build();

        OrderReceipt r1 = OrderReceipt.builder()
                .platform("SWIGGY")
                .platformOrderId("ORD-1")
                .totalAmount(320.0)
                .orderDateTime(now)
                .items(List.of(OrderReceiptItem.builder().itemName("Meal 1").totalPrice(320.0).build()))
                .build();

        Map<UUID, List<TransactionItem>> matched = matcher.match(List.of(t1, t2), List.of(r1));

        assertThat(matched).hasSize(1);
        assertThat(matched).containsKey(txn1);
        assertThat(matched.get(txn1).get(0).platformOrderId()).isEqualTo("ORD-1");
    }

    @Test
    @DisplayName("Credits are not matched with purchase receipts")
    void match_creditDirection_notMatched() {
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(320.0)
                .direction(TxnDirection.CREDIT) // Refund or credit
                .counterparty("SWIGGY")
                .datetime(LocalDateTime.now())
                .build();

        OrderReceipt receipt = OrderReceipt.builder()
                .platform("SWIGGY")
                .totalAmount(320.0)
                .orderDateTime(LocalDateTime.now())
                .items(List.of(OrderReceiptItem.builder().itemName("Burger").totalPrice(320.0).build()))
                .build();

        Map<UUID, List<TransactionItem>> matched = matcher.match(List.of(txn), List.of(receipt));
        assertThat(matched).isEmpty();
    }

    @Test
    @DisplayName("Instamart transaction counterparty matches SWIGGY platform receipt")
    void match_instamartTransaction_matchesSwiggyPlatformReceipt() {
        UUID txnId = UUID.randomUUID();
        LocalDateTime txnTime = LocalDateTime.of(2026, 10, 15, 14, 0);

        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(450.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("RSP*INSTAMART BANGALORE")
                .datetime(txnTime)
                .build();

        OrderReceipt receipt = OrderReceipt.builder()
                .platform("SWIGGY")
                .platformOrderId("INSTA-1234")
                .outletName("Instamart")
                .orderDateTime(txnTime)
                .totalAmount(450.0)
                .items(List.of(
                        OrderReceiptItem.builder().itemName("Milk").quantity(1).totalPrice(30.0).build(),
                        OrderReceiptItem.builder().itemName("Bread").quantity(1).totalPrice(40.0).build()
                ))
                .build();

        Map<UUID, List<TransactionItem>> matched = matcher.match(List.of(txn), List.of(receipt));
        assertThat(matched).containsOnlyKeys(txnId);
        assertThat(matched.get(txnId).get(0).outletName()).isEqualTo("Instamart");
        assertThat(matched.get(txnId).get(0).platform()).isEqualTo("SWIGGY");
    }

    @Test
    @DisplayName("Amount difference within 0.1 threshold matches transaction")
    void match_amountDifferenceWithinThreshold_matches() {
        UUID txnId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(320.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("SWIGGY")
                .datetime(now)
                .build();

        // 8 paise difference - within 0.1 threshold
        OrderReceipt receipt = OrderReceipt.builder()
                .platform("SWIGGY")
                .totalAmount(320.08)
                .orderDateTime(now)
                .items(List.of(OrderReceiptItem.builder().itemName("Meal").totalPrice(320.08).build()))
                .build();

        Map<UUID, List<TransactionItem>> matched = matcher.match(List.of(txn), List.of(receipt));
        assertThat(matched).containsOnlyKeys(txnId);
    }

    @Test
    @DisplayName("Amount difference exceeding 0.1 threshold does not match")
    void match_amountDifferenceExceedingThreshold_doesNotMatch() {
        UUID txnId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(320.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("SWIGGY")
                .datetime(now)
                .build();

        // 15 paise difference - exceeds 0.1 threshold
        OrderReceipt receipt = OrderReceipt.builder()
                .platform("SWIGGY")
                .totalAmount(320.15)
                .orderDateTime(now)
                .items(List.of(OrderReceiptItem.builder().itemName("Meal").totalPrice(320.15).build()))
                .build();

        Map<UUID, List<TransactionItem>> matched = matcher.match(List.of(txn), List.of(receipt));
        assertThat(matched).isEmpty();
    }

    @Test
    @DisplayName("Flexible platform aliases derived from config or custom map match correctly")
    void match_customPlatformAliases_matchesDerivedKeywords() {
        TransactionReceiptMatcher customMatcher = new TransactionReceiptMatcher(
                Map.of("ZEPTO", List.of("ZEPTO", "KIRANAKART"))
        );

        UUID txnId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(150.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("UPI-KIRANAKART BANGALORE")
                .datetime(now)
                .build();

        OrderReceipt receipt = OrderReceipt.builder()
                .platform("ZEPTO")
                .outletName("Zepto Quick")
                .totalAmount(150.0)
                .orderDateTime(now)
                .items(List.of(OrderReceiptItem.builder().itemName("Chips").totalPrice(150.0).build()))
                .build();

        Map<UUID, List<TransactionItem>> matched = customMatcher.match(List.of(txn), List.of(receipt));
        assertThat(matched).containsOnlyKeys(txnId);
        assertThat(matched.get(txnId).get(0).platform()).isEqualTo("ZEPTO");
    }

    @Test
    @DisplayName("Unconfigured platform falls back to matching its own platform name")
    void match_unmappedPlatform_fallsBackToPlatformName() {
        UUID txnId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(500.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("MYSTORE HYDERABAD")
                .datetime(now)
                .build();

        OrderReceipt receipt = OrderReceipt.builder()
                .platform("MYSTORE")
                .totalAmount(500.0)
                .orderDateTime(now)
                .items(List.of(OrderReceiptItem.builder().itemName("Shirt").totalPrice(500.0).build()))
                .build();

        Map<UUID, List<TransactionItem>> matched = matcher.match(List.of(txn), List.of(receipt));
        assertThat(matched).containsOnlyKeys(txnId);
    }

    @Test
    @DisplayName("Custom date window (daysBefore/daysAfter) matches order receipt within custom window")
    void match_customDateWindow_matchesWithinCustomWindow() {
        // daysBefore = 5, daysAfter = 2
        TransactionReceiptMatcher windowMatcher = new TransactionReceiptMatcher(
                0.1, 5, 2, new ConfigPlatformAliasProvider()
        );

        UUID txnId = UUID.randomUUID();
        LocalDateTime txnTime = LocalDateTime.of(2026, 10, 15, 14, 0);
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .amount(300.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("SWIGGY")
                .datetime(txnTime)
                .build();

        // 4 days prior - within 5 days window, but outside default 2 days window
        OrderReceipt receipt = OrderReceipt.builder()
                .platform("SWIGGY")
                .totalAmount(300.0)
                .orderDateTime(txnTime.minusDays(4))
                .items(List.of(OrderReceiptItem.builder().itemName("Biryani").totalPrice(300.0).build()))
                .build();

        Map<UUID, List<TransactionItem>> matched = windowMatcher.match(List.of(txn), List.of(receipt));
        assertThat(matched).containsOnlyKeys(txnId);

        // Verify that default matcher (2 days window) rejects 4 days prior
        Map<UUID, List<TransactionItem>> defaultMatched = matcher.match(List.of(txn), List.of(receipt));
        assertThat(defaultMatched).isEmpty();
    }
}
