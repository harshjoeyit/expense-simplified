package io.github.harshjoeyit.enrichment.repository;

import io.github.harshjoeyit.enrichment.model.TransactionItem;

import java.util.*;

public interface TransactionItemRepository {

    TransactionItem save(TransactionItem item);

    List<TransactionItem> saveAll(List<TransactionItem> items);

    Optional<TransactionItem> findById(Long id);

    List<TransactionItem> findByTransactionId(UUID transactionId);

    Map<UUID, List<TransactionItem>> findByTransactionIds(Collection<UUID> transactionIds);

    void deleteByTransactionId(UUID transactionId);
}
