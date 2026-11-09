package io.github.harshjoeyit.enrichment.repository;

import io.github.harshjoeyit.enrichment.model.TransactionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTransactionItemRepositoryTest {

    private InMemoryTransactionItemRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionItemRepository();
    }

    @Test
    @DisplayName("save assigns auto-incrementing ID")
    void save_assignsId() {
        UUID txnId = UUID.randomUUID();
        TransactionItem item1 = TransactionItem.builder()
                .transactionId(txnId)
                .itemName("Burger")
                .totalPrice(210.0)
                .build();
        TransactionItem item2 = TransactionItem.builder()
                .transactionId(txnId)
                .itemName("Fries")
                .totalPrice(80.0)
                .build();

        TransactionItem saved1 = repository.save(item1);
        TransactionItem saved2 = repository.save(item2);

        assertThat(saved1.id()).isEqualTo(1L);
        assertThat(saved2.id()).isEqualTo(2L);
        assertThat(saved1.itemName()).isEqualTo("Burger");
        assertThat(saved2.itemName()).isEqualTo("Fries");
    }

    @Test
    @DisplayName("save with existing ID updates the item")
    void save_withExistingId_updatesItem() {
        UUID txnId = UUID.randomUUID();
        TransactionItem saved = repository.save(TransactionItem.builder()
                .transactionId(txnId)
                .itemName("Burger")
                .totalPrice(210.0)
                .build());

        TransactionItem updated = TransactionItem.builder()
                .id(saved.id())
                .transactionId(txnId)
                .itemName("Double Burger")
                .totalPrice(250.0)
                .build();
        repository.save(updated);

        Optional<TransactionItem> found = repository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().itemName()).isEqualTo("Double Burger");
        assertThat(found.get().totalPrice()).isEqualTo(250.0);
    }

    @Test
    @DisplayName("saveAll assigns IDs and preserves order")
    void saveAll_assignsIds() {
        UUID txnId = UUID.randomUUID();
        List<TransactionItem> items = List.of(
                TransactionItem.builder().transactionId(txnId).itemName("Item A").totalPrice(100.0).build(),
                TransactionItem.builder().transactionId(txnId).itemName("Item B").totalPrice(200.0).build()
        );

        List<TransactionItem> saved = repository.saveAll(items);

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).id()).isEqualTo(1L);
        assertThat(saved.get(1).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findByTransactionId returns all items for that transaction")
    void findByTransactionId_returnsMatchingItems() {
        UUID txn1 = UUID.randomUUID();
        UUID txn2 = UUID.randomUUID();

        repository.save(TransactionItem.builder().transactionId(txn1).itemName("Item 1").totalPrice(10.0).build());
        repository.save(TransactionItem.builder().transactionId(txn2).itemName("Item 2").totalPrice(20.0).build());
        repository.save(TransactionItem.builder().transactionId(txn1).itemName("Item 3").totalPrice(30.0).build());

        List<TransactionItem> txn1Items = repository.findByTransactionId(txn1);
        assertThat(txn1Items).extracting(TransactionItem::itemName).containsExactly("Item 1", "Item 3");
    }

    @Test
    @DisplayName("findByTransactionIds groups items by transaction ID")
    void findByTransactionIds_groupsByTxnId() {
        UUID txn1 = UUID.randomUUID();
        UUID txn2 = UUID.randomUUID();
        UUID txn3 = UUID.randomUUID();

        repository.save(TransactionItem.builder().transactionId(txn1).itemName("Item 1").totalPrice(10.0).build());
        repository.save(TransactionItem.builder().transactionId(txn2).itemName("Item 2").totalPrice(20.0).build());
        repository.save(TransactionItem.builder().transactionId(txn1).itemName("Item 3").totalPrice(30.0).build());

        Map<UUID, List<TransactionItem>> result = repository.findByTransactionIds(List.of(txn1, txn2, txn3));

        assertThat(result).containsOnlyKeys(txn1, txn2);
        assertThat(result.get(txn1)).hasSize(2);
        assertThat(result.get(txn2)).hasSize(1);
    }

    @Test
    @DisplayName("deleteByTransactionId removes only matching items")
    void deleteByTransactionId_removesMatchingItems() {
        UUID txn1 = UUID.randomUUID();
        UUID txn2 = UUID.randomUUID();

        repository.save(TransactionItem.builder().transactionId(txn1).itemName("Item 1").totalPrice(10.0).build());
        repository.save(TransactionItem.builder().transactionId(txn2).itemName("Item 2").totalPrice(20.0).build());

        repository.deleteByTransactionId(txn1);

        assertThat(repository.findByTransactionId(txn1)).isEmpty();
        assertThat(repository.findByTransactionId(txn2)).hasSize(1);
    }

    @Test
    @DisplayName("save null throws IllegalArgumentException")
    void save_nullThrows() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
