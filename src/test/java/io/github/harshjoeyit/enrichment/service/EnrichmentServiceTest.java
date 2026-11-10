package io.github.harshjoeyit.enrichment.service;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.repository.InMemoryTransactionRepository;
import io.github.harshjoeyit.enrichment.client.ImapEmailReader;
import io.github.harshjoeyit.enrichment.config.EmailEnrichmentConfig;
import io.github.harshjoeyit.enrichment.config.EmailEnrichmentConfig.EmailAccountConfig;
import io.github.harshjoeyit.enrichment.matcher.TransactionReceiptMatcher;
import io.github.harshjoeyit.enrichment.model.EnrichmentStatus;
import io.github.harshjoeyit.enrichment.model.OrderReceipt;
import io.github.harshjoeyit.enrichment.model.OrderReceiptItem;
import io.github.harshjoeyit.enrichment.model.TransactionItem;
import io.github.harshjoeyit.enrichment.repository.InMemoryTransactionItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichmentServiceTest {

    private InMemoryTransactionItemRepository itemRepository;
    private InMemoryTransactionRepository transactionRepository;
    private TransactionReceiptMatcher matcher;
    private StubEmailReader emailReader;

    @BeforeEach
    void setUp() {
        itemRepository = new InMemoryTransactionItemRepository();
        transactionRepository = new InMemoryTransactionRepository();
        matcher = new TransactionReceiptMatcher();
        emailReader = new StubEmailReader();
    }

    @Test
    @DisplayName("enrichStatementAsync returns NOT_STARTED if plugin is disabled")
    void enrichStatementAsync_whenDisabled_marksNotStarted() {
        EmailEnrichmentConfig props = new EmailEnrichmentConfig(false, null, null, Collections.emptyList());
        EnrichmentService service = new EnrichmentService(props, emailReader, matcher, itemRepository, transactionRepository);

        UUID stmtId = UUID.randomUUID();
        CompletableFuture<Void> future = service.enrichStatementAsync(stmtId);
        future.join();

        assertThat(service.getStatus(stmtId)).isEqualTo(EnrichmentStatus.NOT_STARTED);
    }

    @Test
    @DisplayName("enrichStatementAsync fetches receipts, matches, and saves items")
    void enrichStatementAsync_whenEnabled_enrichesAndMarksCompleted() {
        EmailAccountConfig account = new EmailAccountConfig(
                "Personal", "imap.gmail.com", 993, true, "test@gmail.com", "pass", List.of("SWIGGY"), 15, List.of("INBOX")
        );
        EmailEnrichmentConfig props = new EmailEnrichmentConfig(true, null, null, List.of(account));
        EnrichmentService service = new EnrichmentService(props, emailReader, matcher, itemRepository, transactionRepository);

        UUID stmtId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        transactionRepository.saveTransaction(NormalizedTransaction.builder()
                .id(txnId)
                .statementId(stmtId)
                .amount(290.0)
                .direction(TxnDirection.DEBIT)
                .counterparty("SWIGGY")
                .datetime(now)
                .rawTxn("raw")
                .build());

        emailReader.receiptsToReturn = List.of(OrderReceipt.builder()
                .platform("SWIGGY")
                .outletName("Truffles")
                .totalAmount(290.0)
                .orderDateTime(now)
                .items(List.of(
                        OrderReceiptItem.builder().itemName("Burger").totalPrice(210.0).build(),
                        OrderReceiptItem.builder().itemName("Fries").totalPrice(80.0).build()
                ))
                .build());

        CompletableFuture<Void> future = service.enrichStatementAsync(stmtId);
        future.join();

        assertThat(service.getStatus(stmtId)).isEqualTo(EnrichmentStatus.COMPLETED);
        List<TransactionItem> items = itemRepository.findByTransactionId(txnId);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).itemName()).isEqualTo("Burger");
        assertThat(items.get(1).itemName()).isEqualTo("Fries");
    }

    @Test
    @DisplayName("enrichStatementAsync records FAILED status if exception occurs")
    void enrichStatementAsync_whenException_marksFailed() {
        EmailAccountConfig account = new EmailAccountConfig(
                "Personal", "imap.gmail.com", 993, true, "test@gmail.com", "pass", List.of("SWIGGY"), 15, List.of("INBOX")
        );
        EmailEnrichmentConfig props = new EmailEnrichmentConfig(true, null, null, List.of(account));
        emailReader.throwException = true;
        EnrichmentService service = new EnrichmentService(props, emailReader, matcher, itemRepository, transactionRepository);

        UUID stmtId = UUID.randomUUID();
        transactionRepository.saveTransaction(NormalizedTransaction.builder()
                .id(UUID.randomUUID())
                .statementId(stmtId)
                .amount(100.0)
                .direction(TxnDirection.DEBIT)
                .datetime(LocalDateTime.now())
                .rawTxn("raw")
                .build());

        CompletableFuture<Void> future = service.enrichStatementAsync(stmtId);
        future.join();

        assertThat(service.getStatus(stmtId)).isEqualTo(EnrichmentStatus.FAILED);
        assertThat(service.getErrorMessage(stmtId)).contains("IMAP connection timeout");
    }

    @Test
    @DisplayName("enrichStatementAsync respects searchDaysBefore and searchDaysAfter parameters")
    void enrichStatementAsync_respectsSearchWindowBuffer() {
        EmailAccountConfig account = new EmailAccountConfig(
                "Personal", "imap.gmail.com", 993, true, "test@gmail.com", "pass", List.of("SWIGGY"), 15, List.of("INBOX")
        );
        EmailEnrichmentConfig props = new EmailEnrichmentConfig(true, null, null, List.of(account));
        EnrichmentService service = new EnrichmentService(
                props, emailReader, matcher, itemRepository, transactionRepository, 5, 3
        );

        UUID stmtId = UUID.randomUUID();
        LocalDate txnDate = LocalDate.of(2026, 10, 15);
        transactionRepository.saveTransaction(NormalizedTransaction.builder()
                .id(UUID.randomUUID())
                .statementId(stmtId)
                .amount(100.0)
                .direction(TxnDirection.DEBIT)
                .datetime(txnDate.atTime(12, 0))
                .build());

        service.enrichStatementAsync(stmtId).join();

        assertThat(emailReader.capturedStartDate).isEqualTo(txnDate.minusDays(5));
        assertThat(emailReader.capturedEndDate).isEqualTo(txnDate.plusDays(3));
    }

    private static class StubEmailReader extends ImapEmailReader {
        List<OrderReceipt> receiptsToReturn = Collections.emptyList();
        boolean throwException = false;
        LocalDate capturedStartDate;
        LocalDate capturedEndDate;

        public StubEmailReader() {
            super(Collections.emptyList());
        }

        @Override
        public List<OrderReceipt> fetchReceipts(EmailAccountConfig account, LocalDate startDate, LocalDate endDate) {
            this.capturedStartDate = startDate;
            this.capturedEndDate = endDate;
            if (throwException) {
                throw new RuntimeException("IMAP connection timeout");
            }
            return receiptsToReturn;
        }
    }
}
