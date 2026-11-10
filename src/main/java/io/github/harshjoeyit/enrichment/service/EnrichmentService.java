package io.github.harshjoeyit.enrichment.service;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import io.github.harshjoeyit.enrichment.client.ImapEmailReader;
import io.github.harshjoeyit.enrichment.config.EmailEnrichmentConfig;
import io.github.harshjoeyit.enrichment.config.EmailEnrichmentConfig.EmailAccountConfig;
import io.github.harshjoeyit.enrichment.matcher.TransactionReceiptMatcher;
import io.github.harshjoeyit.enrichment.model.EnrichmentStatus;
import io.github.harshjoeyit.enrichment.model.OrderReceipt;
import io.github.harshjoeyit.enrichment.model.TransactionItem;
import io.github.harshjoeyit.enrichment.repository.TransactionItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EnrichmentService {

    public static final int DEFAULT_SEARCH_DAYS_BEFORE = 2;
    public static final int DEFAULT_SEARCH_DAYS_AFTER = 1;

    private final EmailEnrichmentConfig config;
    private final ImapEmailReader emailReader;
    private final TransactionReceiptMatcher matcher;
    private final TransactionItemRepository itemRepository;
    private final TransactionRepository transactionRepository;
    private final int searchDaysBefore;
    private final int searchDaysAfter;

    private final Map<UUID, EnrichmentStatus> statusMap = new ConcurrentHashMap<>();
    private final Map<UUID, String> errorMap = new ConcurrentHashMap<>();

    @Autowired
    public EnrichmentService(
            EmailEnrichmentConfig config,
            ImapEmailReader emailReader,
            TransactionReceiptMatcher matcher,
            TransactionItemRepository itemRepository,
            TransactionRepository transactionRepository
    ) {
        this(config, emailReader, matcher, itemRepository, transactionRepository,
                DEFAULT_SEARCH_DAYS_BEFORE, DEFAULT_SEARCH_DAYS_AFTER);
    }

    public EnrichmentService(
            EmailEnrichmentConfig config,
            ImapEmailReader emailReader,
            TransactionReceiptMatcher matcher,
            TransactionItemRepository itemRepository,
            TransactionRepository transactionRepository,
            int searchDaysBefore,
            int searchDaysAfter
    ) {
        this.config = config;
        this.emailReader = emailReader;
        this.matcher = matcher;
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
        this.searchDaysBefore = searchDaysBefore;
        this.searchDaysAfter = searchDaysAfter;
    }

    public boolean isEnabled() {
        return config != null && config.enabled()
                && config.accounts() != null && !config.accounts().isEmpty();
    }

    public EnrichmentStatus getStatus(UUID statementId) {
        if (statementId == null) {
            return EnrichmentStatus.NOT_STARTED;
        }
        return statusMap.getOrDefault(statementId, EnrichmentStatus.NOT_STARTED);
    }

    public String getErrorMessage(UUID statementId) {
        if (statementId == null) {
            return null;
        }
        return errorMap.get(statementId);
    }

    public CompletableFuture<Void> enrichStatementAsync(UUID statementId) {
        if (statementId == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (!isEnabled()) {
            log.debug("Email enrichment plugin is disabled or unconfigured, skipping for statement [{}]", statementId);
            statusMap.put(statementId, EnrichmentStatus.NOT_STARTED);
            return CompletableFuture.completedFuture(null);
        }

        statusMap.put(statementId, EnrichmentStatus.IN_PROGRESS);
        errorMap.remove(statementId);
        log.info("Starting asynchronous email enrichment for statement [{}]", statementId);

        return CompletableFuture.runAsync(() -> {
            try {
                List<NormalizedTransaction> txns = transactionRepository.findByStatementId(statementId);
                if (txns.isEmpty()) {
                    log.info("No transactions found for statement [{}], enrichment marked COMPLETED", statementId);
                    statusMap.put(statementId, EnrichmentStatus.COMPLETED);
                    return;
                }

                LocalDate minDate = txns.stream()
                        .map(NormalizedTransaction::getDatetime)
                        .filter(Objects::nonNull)
                        .map(LocalDateTime::toLocalDate)
                        .min(LocalDate::compareTo)
                        .orElse(LocalDate.now());

                LocalDate maxDate = txns.stream()
                        .map(NormalizedTransaction::getDatetime)
                        .filter(Objects::nonNull)
                        .map(LocalDateTime::toLocalDate)
                        .max(LocalDate::compareTo)
                        .orElse(LocalDate.now());

                LocalDate searchStart = minDate.minusDays(searchDaysBefore);
                LocalDate searchEnd = maxDate.plusDays(searchDaysAfter);

                List<OrderReceipt> allReceipts = new ArrayList<>();
                for (EmailAccountConfig account : config.accounts()) {
                    try {
                        List<OrderReceipt> receipts = emailReader.fetchReceipts(account, searchStart, searchEnd);
                        allReceipts.addAll(receipts);
                    } catch (Exception e) {
                        log.warn("Failed fetching receipts from account [{}]: {}", account.name(), e.getMessage());
                        throw e;
                    }
                }

                Map<UUID, List<TransactionItem>> matched = matcher.match(txns, allReceipts);

                for (Map.Entry<UUID, List<TransactionItem>> entry : matched.entrySet()) {
                    itemRepository.deleteByTransactionId(entry.getKey());
                    itemRepository.saveAll(entry.getValue());
                }

                statusMap.put(statementId, EnrichmentStatus.COMPLETED);
                log.info("Email enrichment successfully COMPLETED for statement [{}]: enriched {} transactions",
                        statementId, matched.size());

            } catch (Exception e) {
                log.error("Email enrichment FAILED for statement [{}]: {}", statementId, e.getMessage(), e);
                statusMap.put(statementId, EnrichmentStatus.FAILED);
                errorMap.put(statementId, e.getMessage());
            }
        });
    }

    public void retryEnrichment(UUID statementId) {
        if (statementId != null) {
            errorMap.remove(statementId);
            enrichStatementAsync(statementId);
        }
    }

    public List<TransactionItem> getItemsForTransaction(UUID transactionId) {
        if (transactionId == null) {
            return Collections.emptyList();
        }
        return itemRepository.findByTransactionId(transactionId);
    }
}
