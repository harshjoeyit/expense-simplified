package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<UUID, NormalizedTransaction> transactions = new ConcurrentHashMap<>();

    @Override
    public Optional<NormalizedTransaction> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(transactions.get(id));
    }

    @Override
    public List<NormalizedTransaction> findByStatementId(UUID statementId) {
        return transactions.values()
                .stream()
                .filter(transaction -> Objects.equals(transaction.getStatementId(), statementId))
                .toList();
    }

    @Override
    public List<NormalizedTransaction> findPendingReviewTransactionsByStatementId(UUID statementId) {
        List<NormalizedTransaction> pending = transactions.values()
                .stream()
                .filter(transaction -> Objects.equals(transaction.getStatementId(), statementId)
                        && transaction.getReviewStatus() == TxnReviewStatus.PENDING)
                .toList();

        log.debug("found {} pending review transactions for statement: {}", pending.size(), statementId);

        return pending;
    }

    @Override
    public void saveTransaction(NormalizedTransaction transaction) {
        if (transaction == null || transaction.getId() == null) {
            log.warn("Cannot save a null or save transaction without Id");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        NormalizedTransaction existing = transactions.get(transaction.getId());

        if (existing == null) {
            if (transaction.getCreatedAt() == null) {
                transaction.setCreatedAt(now);
            }
            if (transaction.getUpdatedAt() == null) {
                transaction.setUpdatedAt(now);
            }
            transactions.put(transaction.getId(), transaction);
        } else {
            transaction.setCreatedAt(existing.getCreatedAt());
            transaction.setUpdatedAt(now);
            transactions.put(transaction.getId(), transaction);
        }
        log.info("updated transaction: {}", transaction.getId());
    }

    @Override
    public void saveTransactions(List<NormalizedTransaction> transactions) {
        if (transactions != null) {
            transactions.forEach(this::saveTransaction);
        }
    }
}
