package io.github.harshjoeyit.enrichment.matcher;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.enrichment.model.OrderReceipt;
import io.github.harshjoeyit.enrichment.model.OrderReceiptItem;
import io.github.harshjoeyit.enrichment.model.TransactionItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class TransactionReceiptMatcher {

    public static final double DEFAULT_AMOUNT_THRESHOLD = 0.1;
    public static final int DEFAULT_DAYS_BEFORE = 2;
    public static final int DEFAULT_DAYS_AFTER = 1;

    private final double amountThreshold;
    private final int daysBefore;
    private final int daysAfter;
    private final PlatformAliasProvider platformAliasProvider;

    public TransactionReceiptMatcher() {
        this(DEFAULT_AMOUNT_THRESHOLD, DEFAULT_DAYS_BEFORE, DEFAULT_DAYS_AFTER, new ConfigPlatformAliasProvider());
    }

    @Autowired
    public TransactionReceiptMatcher(PlatformAliasProvider platformAliasProvider) {
        this(DEFAULT_AMOUNT_THRESHOLD, DEFAULT_DAYS_BEFORE, DEFAULT_DAYS_AFTER, platformAliasProvider);
    }

    public TransactionReceiptMatcher(Map<String, List<String>> customAliases) {
        this(DEFAULT_AMOUNT_THRESHOLD, DEFAULT_DAYS_BEFORE, DEFAULT_DAYS_AFTER, new ConfigPlatformAliasProvider(customAliases));
    }

    public TransactionReceiptMatcher(double amountThreshold, PlatformAliasProvider platformAliasProvider) {
        this(amountThreshold, DEFAULT_DAYS_BEFORE, DEFAULT_DAYS_AFTER, platformAliasProvider);
    }

    public TransactionReceiptMatcher(
            double amountThreshold,
            int daysBefore,
            int daysAfter,
            PlatformAliasProvider platformAliasProvider
    ) {
        this.amountThreshold = amountThreshold;
        this.daysBefore = daysBefore;
        this.daysAfter = daysAfter;
        this.platformAliasProvider = platformAliasProvider != null
                ? platformAliasProvider
                : new ConfigPlatformAliasProvider();
    }

    public Map<UUID, List<TransactionItem>> match(
            List<NormalizedTransaction> transactions,
            List<OrderReceipt> receipts
    ) {
        if (transactions == null || transactions.isEmpty() || receipts == null || receipts.isEmpty()) {
            log.warn("Require non-null/non-empty transactions and receipts to match, found transactions: {}, receipts: {}",
                    transactions, receipts);
            return Collections.emptyMap();
        }

        Set<OrderReceipt> usedReceipts = new HashSet<>();
        Map<UUID, List<TransactionItem>> result = new LinkedHashMap<>();

        for (NormalizedTransaction txn : transactions) {
            if (txn.getDirection() != TxnDirection.DEBIT) {
                continue;
            }

            // Best match is where email and transaction date is closest, given
            // platform matches, amount matches with threshold and email is within duration
            OrderReceipt bestMatch = null;
            long bestMatchDiffMinutes = Long.MAX_VALUE;

            for (OrderReceipt receipt : receipts) {
                if (usedReceipts.contains(receipt)) {
                    // Skip if order already tagged to another transaction
                    continue;
                }

                if (matchesPlatform(txn, receipt.platform())
                        && isAmountMatching(txn.getAmount(), receipt.totalAmount())
                        && isDateNear(txn.getDatetime(), receipt.orderDateTime())) {

                    long diff = txn.getDatetime() != null
                            ? Math.abs(Duration.between(txn.getDatetime(), receipt.orderDateTime()).toMinutes())
                            : 0;

                    if (diff < bestMatchDiffMinutes) {
                        bestMatchDiffMinutes = diff;
                        bestMatch = receipt;
                    }
                }
            }

            if (bestMatch != null) {
                usedReceipts.add(bestMatch);
                List<TransactionItem> items = new ArrayList<>();
                for (OrderReceiptItem rItem : bestMatch.items()) {
                    items.add(TransactionItem.builder()
                            .transactionId(txn.getId())
                            .itemName(rItem.itemName())
                            .quantity(rItem.quantity())
                            .unitPrice(rItem.unitPrice())
                            .totalPrice(rItem.totalPrice())
                            .outletName(bestMatch.outletName())
                            .platform(bestMatch.platform())
                            .platformOrderId(bestMatch.platformOrderId())
                            .createdAt(LocalDateTime.now())
                            .build());
                }
                result.put(txn.getId(), items);
                log.debug("Matched txn [{}] (₹{}) with {} order [{}] ({} items)",
                        txn.getId(), txn.getAmount(), bestMatch.platform(), bestMatch.platformOrderId(), items.size());
            }
        }

        log.info("Matched {} of {} candidate transactions with email order receipts",
                result.size(), transactions.size());
        return result;
    }

    private boolean matchesPlatform(NormalizedTransaction txn, String platform) {
        if (platform == null) {
            return false;
        }
        String cp = txn.getCounterparty() != null ? txn.getCounterparty().toUpperCase(Locale.ROOT) : "";
        String raw = txn.getRawTxn() != null ? txn.getRawTxn().toUpperCase(Locale.ROOT) : "";
        String combined = cp + " " + raw;

        List<String> aliases = platformAliasProvider.getAliases(platform);
        if (aliases == null || aliases.isEmpty()) {
            return combined.contains(platform.toUpperCase(Locale.ROOT));
        }

        for (String alias : aliases) {
            if (alias != null && !alias.isBlank() && combined.contains(alias.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isAmountMatching(double txnAmount, double receiptTotal) {
        return Math.abs(txnAmount - receiptTotal) <= amountThreshold + 1e-6;
    }

    private boolean isDateNear(LocalDateTime txnDate, LocalDateTime orderReceiptDate) {
        if (txnDate == null || orderReceiptDate == null) {
            return false;
        }
        LocalDate tDate = txnDate.toLocalDate();
        LocalDate oDate = orderReceiptDate.toLocalDate();
        // Order receipt date is within [tDate - daysBefore, tDate + daysAfter]
        return !oDate.isBefore(tDate.minusDays(daysBefore)) && !oDate.isAfter(tDate.plusDays(daysAfter));
    }
}
