package io.github.harshjoeyit.enrichment.repository;

import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.StatementReviewStatus;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnType;
import io.github.harshjoeyit.core.repository.JdbcStatementRepository;
import io.github.harshjoeyit.core.repository.JdbcTransactionRepository;
import io.github.harshjoeyit.enrichment.model.TransactionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.persistence=sqlite",
        "spring.shell.interactive.enabled=false",
        "spring.datasource.url=jdbc:sqlite:target/test-expense-simplified.db?journal_mode=WAL&foreign_keys=ON"
})
@TestExecutionListeners(listeners = {
        DependencyInjectionTestExecutionListener.class
})
class JdbcTransactionItemRepositoryTest {

    @Autowired
    private JdbcTransactionItemRepository itemRepository;

    @Autowired
    private JdbcTransactionRepository transactionRepository;

    @Autowired
    private JdbcStatementRepository statementRepository;

    @Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbcClient;

    private UUID statementId;
    private UUID transactionId1;
    private UUID transactionId2;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM transaction_items").update();

        statementId = UUID.randomUUID();
        statementRepository.saveStatement(new Statement(
                statementId,
                FileType.DELIMITED,
                "test.csv",
                "HDFC_SAVINGS",
                StatementReviewStatus.PENDING_REVIEW,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));

        transactionId1 = UUID.randomUUID();
        transactionRepository.saveTransaction(NormalizedTransaction.builder()
                .id(transactionId1)
                .statementId(statementId)
                .instrumentId("HDFC_SAVINGS")
                .datetime(LocalDateTime.now())
                .amount(290.0)
                .direction(TxnDirection.DEBIT)
                .type(TxnType.PURCHASE)
                .counterparty("SWIGGY")
                .reviewStatus(io.github.harshjoeyit.core.model.TxnReviewStatus.PENDING)
                .rawTxn("raw1")
                .build());

        transactionId2 = UUID.randomUUID();
        transactionRepository.saveTransaction(NormalizedTransaction.builder()
                .id(transactionId2)
                .statementId(statementId)
                .instrumentId("HDFC_SAVINGS")
                .datetime(LocalDateTime.now())
                .amount(540.0)
                .direction(TxnDirection.DEBIT)
                .type(TxnType.PURCHASE)
                .counterparty("SWIGGY")
                .reviewStatus(io.github.harshjoeyit.core.model.TxnReviewStatus.PENDING)
                .rawTxn("raw2")
                .build());
    }

    @Test
    @DisplayName("save assigns auto-increment ID and persists in SQLite")
    void save_persistsItemWithGeneratedId() {
        TransactionItem item = TransactionItem.builder()
                .transactionId(transactionId1)
                .itemName("All American Burger")
                .quantity(1)
                .unitPrice(210.0)
                .totalPrice(210.0)
                .outletName("Truffles")
                .platform("SWIGGY")
                .platformOrderId("ORD12345")
                .build();

        TransactionItem saved = itemRepository.save(item);

        assertThat(saved.id()).isNotNull().isPositive();
        assertThat(saved.itemName()).isEqualTo("All American Burger");
        assertThat(saved.outletName()).isEqualTo("Truffles");
        assertThat(saved.platform()).isEqualTo("SWIGGY");

        Optional<TransactionItem> found = itemRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().itemName()).isEqualTo("All American Burger");
        assertThat(found.get().totalPrice()).isEqualTo(210.0);
    }

    @Test
    @DisplayName("saveAll persists multiple items")
    void saveAll_persistsMultipleItems() {
        List<TransactionItem> items = List.of(
                TransactionItem.builder()
                        .transactionId(transactionId1)
                        .itemName("Burger")
                        .totalPrice(210.0)
                        .build(),
                TransactionItem.builder()
                        .transactionId(transactionId1)
                        .itemName("Fries")
                        .totalPrice(80.0)
                        .build()
        );

        List<TransactionItem> saved = itemRepository.saveAll(items);

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).id()).isNotNull();
        assertThat(saved.get(1).id()).isNotNull();
        assertThat(saved.get(0).id()).isNotEqualTo(saved.get(1).id());
    }

    @Test
    @DisplayName("findByTransactionId returns items in ascending ID order")
    void findByTransactionId_returnsItems() {
        itemRepository.save(TransactionItem.builder().transactionId(transactionId1).itemName("Dish 1").totalPrice(100.0).build());
        itemRepository.save(TransactionItem.builder().transactionId(transactionId2).itemName("Dish 2").totalPrice(200.0).build());
        itemRepository.save(TransactionItem.builder().transactionId(transactionId1).itemName("Dish 3").totalPrice(300.0).build());

        List<TransactionItem> items = itemRepository.findByTransactionId(transactionId1);

        assertThat(items).hasSize(2);
        assertThat(items).extracting(TransactionItem::itemName).containsExactly("Dish 1", "Dish 3");
    }

    @Test
    @DisplayName("findByTransactionIds groups items for multiple transactions")
    void findByTransactionIds_groupsCorrectly() {
        itemRepository.save(TransactionItem.builder().transactionId(transactionId1).itemName("Dish 1").totalPrice(100.0).build());
        itemRepository.save(TransactionItem.builder().transactionId(transactionId2).itemName("Dish 2").totalPrice(200.0).build());

        Map<UUID, List<TransactionItem>> result = itemRepository.findByTransactionIds(List.of(transactionId1, transactionId2));

        assertThat(result).containsOnlyKeys(transactionId1, transactionId2);
        assertThat(result.get(transactionId1)).hasSize(1);
        assertThat(result.get(transactionId2)).hasSize(1);
    }

    @Test
    @DisplayName("update existing item modifies attributes")
    void update_modifiesAttributes() {
        TransactionItem saved = itemRepository.save(TransactionItem.builder()
                .transactionId(transactionId1)
                .itemName("Old Name")
                .totalPrice(100.0)
                .build());

        TransactionItem updated = TransactionItem.builder()
                .id(saved.id())
                .transactionId(transactionId1)
                .itemName("New Name")
                .totalPrice(150.0)
                .outletName("New Outlet")
                .build();
        itemRepository.save(updated);

        Optional<TransactionItem> found = itemRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().itemName()).isEqualTo("New Name");
        assertThat(found.get().totalPrice()).isEqualTo(150.0);
        assertThat(found.get().outletName()).isEqualTo("New Outlet");
    }

    @Test
    @DisplayName("deleteByTransactionId deletes only items for that transaction")
    void deleteByTransactionId_removesItems() {
        itemRepository.save(TransactionItem.builder().transactionId(transactionId1).itemName("Dish 1").totalPrice(100.0).build());
        itemRepository.save(TransactionItem.builder().transactionId(transactionId2).itemName("Dish 2").totalPrice(200.0).build());

        itemRepository.deleteByTransactionId(transactionId1);

        assertThat(itemRepository.findByTransactionId(transactionId1)).isEmpty();
        assertThat(itemRepository.findByTransactionId(transactionId2)).hasSize(1);
    }

    @Test
    @DisplayName("ON DELETE CASCADE: Deleting statement cascades to transactions and transaction_items")
    void cascadeDelete_whenStatementDeleted() {
        itemRepository.save(TransactionItem.builder().transactionId(transactionId1).itemName("Dish 1").totalPrice(100.0).build());
        itemRepository.save(TransactionItem.builder().transactionId(transactionId2).itemName("Dish 2").totalPrice(200.0).build());

        assertThat(itemRepository.findByTransactionId(transactionId1)).hasSize(1);
        assertThat(itemRepository.findByTransactionId(transactionId2)).hasSize(1);

        // Delete statement -> cascade deletes transactions -> cascade deletes transaction_items
        jdbcClient.sql("DELETE FROM statements WHERE id = :id").param("id", statementId.toString()).update();

        assertThat(transactionRepository.findByStatementId(statementId)).isEmpty();
        assertThat(itemRepository.findByTransactionId(transactionId1)).isEmpty();
        assertThat(itemRepository.findByTransactionId(transactionId2)).isEmpty();
    }
}
