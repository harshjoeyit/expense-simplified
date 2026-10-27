package io.github.harshjoeyit.split.repository;

import io.github.harshjoeyit.split.model.TransactionSplit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SplitRepository {
    Optional<TransactionSplit> findById(UUID id);
    List<TransactionSplit> findSplitsByTxnId(UUID txnId);
    List<TransactionSplit> findPendingSplits(int limit);
    List<TransactionSplit> findAll(int limit);
    void deleteSplitsByTxnId(UUID txnId);
    void saveSplit(TransactionSplit txnSplit);
    void markAsPosted(UUID splitId);

    default void saveSplits(List<TransactionSplit> splits) {
        if (splits != null) {
            splits.forEach(this::saveSplit);
        }
    }
}
