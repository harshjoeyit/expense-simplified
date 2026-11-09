package io.github.harshjoeyit.enrichment.repository;

import io.github.harshjoeyit.enrichment.model.TransactionItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Repository
@ConditionalOnProperty(name = "app.persistence", havingValue = "in-memory")
public class InMemoryTransactionItemRepository implements TransactionItemRepository {

    private final Map<Long, TransactionItem> items = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @Override
    public TransactionItem save(TransactionItem item) {
        if (item == null) {
            throw new IllegalArgumentException("TransactionItem must not be null");
        }
        Long id = item.id();
        if (id == null) {
            id = idSequence.getAndIncrement();
        } else {
            idSequence.accumulateAndGet(id + 1, Math::max);
        }
        TransactionItem saved = item.withId(id);
        items.put(id, saved);
        return saved;
    }

    @Override
    public List<TransactionItem> saveAll(List<TransactionItem> itemsToSave) {
        if (itemsToSave == null || itemsToSave.isEmpty()) {
            return Collections.emptyList();
        }
        return itemsToSave.stream()
                .map(this::save)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TransactionItem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public List<TransactionItem> findByTransactionId(UUID transactionId) {
        if (transactionId == null) {
            return Collections.emptyList();
        }
        return items.values().stream()
                .filter(item -> transactionId.equals(item.transactionId()))
                .sorted(Comparator.comparing(TransactionItem::id))
                .collect(Collectors.toList());
    }

    @Override
    public Map<UUID, List<TransactionItem>> findByTransactionIds(Collection<UUID> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> idSet = new HashSet<>(transactionIds);
        return items.values().stream()
                .filter(item -> idSet.contains(item.transactionId()))
                .sorted(Comparator.comparing(TransactionItem::id))
                .collect(Collectors.groupingBy(TransactionItem::transactionId));
    }

    @Override
    public void deleteByTransactionId(UUID transactionId) {
        if (transactionId == null) {
            return;
        }
        items.values().removeIf(item -> transactionId.equals(item.transactionId()));
    }
}
