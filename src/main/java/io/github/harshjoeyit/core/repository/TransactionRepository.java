package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.NormalizedTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {
    Optional<NormalizedTransaction> findById(UUID id);
    List<NormalizedTransaction> findByStatementId(UUID statementId);
    List<NormalizedTransaction> findPendingReviewTransactionsByStatementId(UUID statementId);
    void saveTransaction(NormalizedTransaction transaction);

    default void saveTransactions(List<NormalizedTransaction> transactions) {
        if (transactions != null) {
            transactions.forEach(this::saveTransaction);
        }
    }
}
