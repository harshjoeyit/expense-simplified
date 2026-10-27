package io.github.harshjoeyit.split.repository;

import io.github.harshjoeyit.split.model.SplitStatus;
import io.github.harshjoeyit.split.model.TransactionSplit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InMemorySplitRepository implements SplitRepository {

    Map<UUID, TransactionSplit> splits = new ConcurrentHashMap<>();

    @Override
    public Optional<TransactionSplit> findById(UUID id) {
        return Optional.ofNullable(splits.get(id));
    }

    @Override
    public List<TransactionSplit> findSplitsByTxnId(UUID txnId) {
        return splits.values()
                .stream()
                .filter(split -> split.transactionId().equals(txnId))
                .toList();
    }

    @Override
    public List<TransactionSplit> findPendingSplits(int limit) {
        if (limit <= 0) {
            log.warn("Invalid limit: {}. Expected a positive integer.", limit);
            return List.of();
        }

        return splits.values()
                .stream()
                .filter(split -> split.status().equals(SplitStatus.PENDING))
                .sorted(Comparator.comparing(TransactionSplit::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    @Override
    public List<TransactionSplit> findAll(int limit) {
        if (limit <= 0) {
            log.warn("Invalid limit: {}. Expected a positive integer.", limit);
            return List.of();
        }

        return splits.values()
                .stream()
                .sorted(Comparator.comparing(TransactionSplit::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    @Override
    public void deleteSplitsByTxnId(UUID txnId) {
        if (txnId == null) {
            log.warn("Failed to delete splits. Expected txnId to be non-null");
            return;
        }
        splits.entrySet().removeIf(entry -> txnId.equals(entry.getValue().transactionId()));
    }

    @Override
    public void saveSplit(TransactionSplit split) {
        if (split == null) {
            log.warn("Failed to save. Invalid split: {}. Expected non-null", split);
            return;
        }
        if (split.id() == null) {
            log.warn("Failed to save. Invalid split: {}. Expected id to be non-null", split);
            return;
        }

        TransactionSplit existingSplit = splits.get(split.id());

        if (existingSplit == null) {
            // if not exists, create
            splits.put(split.id(), split);
        } else {
            // update
            TransactionSplit updatedSplit = TransactionSplit.builder()
                    .id(split.id())
                    .transactionId(split.transactionId())
                    .friendId(split.friendId())
                    .friendName(split.friendName())
                    .amount(split.amount())
                    .strategy(split.strategy())
                    .status(split.status())
                    .createdAt(existingSplit.createdAt())
                    .build();
            splits.put(split.id(), updatedSplit);
        }
    }

    @Override
    public void markAsPosted(UUID splitId) {
        TransactionSplit existingSplit = splits.get(splitId);
        // if not exists, create
        if (existingSplit == null) {
            log.error("Failed to save. Could not find split: {}", splitId);
            return;
        }

        splits.put(splitId, existingSplit.withMarkPosted());
    }
}
