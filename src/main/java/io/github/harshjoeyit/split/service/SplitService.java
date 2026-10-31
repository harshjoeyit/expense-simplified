package io.github.harshjoeyit.split.service;

import io.github.harshjoeyit.core.exception.InvalidTransactionId;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import io.github.harshjoeyit.split.InvalidSplitException;
import io.github.harshjoeyit.split.model.Friend;
import io.github.harshjoeyit.split.model.SplitInput;
import io.github.harshjoeyit.split.model.SplitResult;
import io.github.harshjoeyit.split.model.TransactionSplit;
import io.github.harshjoeyit.split.repository.SplitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
public class SplitService {

    private final TransactionRepository transactionRepository;
    private final SplitRepository splitRepository;

    public SplitService(
            TransactionRepository transactionRepository,
            SplitRepository splitRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.splitRepository = splitRepository;
    }

    public SplitResult split(UUID txnId, SplitInput splitInput)
            throws InvalidTransactionId, InvalidSplitException {

        NormalizedTransaction txn = transactionRepository
                .findById(txnId)
                .orElseThrow(() -> new InvalidTransactionId("Invalid txn id:" + txnId));

        if (!Objects.equals(txn.getAmount(), splitInput.amount())) {
            throw new InvalidSplitException("Transaction amount is different from total split amount");
        }

        validateSplitInput(splitInput);

        splitRepository.deleteSplitsByTxnId(txnId);

        return switch (splitInput.strategy()) {
            case EQUAL -> splitEqualAndSave(txn, splitInput);
            case CUSTOM -> splitCustomAndSave(txn, splitInput);
            case SHARE -> splitShareAndSave(txn, splitInput);
            case PERCENT -> splitPercentAndSave(txn, splitInput);
        };
    }

    private SplitResult splitShareAndSave(NormalizedTransaction txn, SplitInput splitInput) {
        List<TransactionSplit> splits = new ArrayList<>();
        boolean selfInvolved = false;
        double selfShare = 0.0;

        double totalShares = splitInput.valuePerFriend().values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        double agg = 0.0;
        int i = 0;
        int friendCount = splitInput.valuePerFriend().size();

        for (Friend friend : splitInput.valuePerFriend().keySet()) {
            double share = splitInput.valuePerFriend().get(friend);
            double shareAmt;
            if (i == friendCount - 1) {
                shareAmt = roundToTwoDecimals(splitInput.amount() - agg);
            } else {
                shareAmt = roundToTwoDecimals(splitInput.amount() * share / totalShares);
            }

            if (friend.isSelf()) {
                selfInvolved = true;
                selfShare = shareAmt;
            } else {
                TransactionSplit split = TransactionSplit.builder()
                        .transactionId(txn.getId())
                        .friendId(friend.id())
                        .friendName(friend.name())
                        .amount(shareAmt)
                        .strategy(splitInput.strategy())
                        .build();

                splits.add(split);
            }

            agg += shareAmt;
            i++;
        }

        // Todo: following two ops should be part of txn (to be atomic) when DB is implemented.
        splitRepository.saveSplits(splits);
        double myShare = selfInvolved ? selfShare : 0.0;
        txn.setMyShare(myShare);
        transactionRepository.saveTransaction(txn);
        return new SplitResult(myShare, splits);
    }

    private SplitResult splitPercentAndSave(NormalizedTransaction txn, SplitInput splitInput) {
        List<TransactionSplit> splits = new ArrayList<>();
        boolean selfInvolved = false;
        double selfShare = 0.0;

        double totalShares = 100.0;
        double agg = 0.0;
        int i = 0;
        int friendCount = splitInput.valuePerFriend().size();

        for (Friend friend : splitInput.valuePerFriend().keySet()) {
            double percentShare = splitInput.valuePerFriend().get(friend);
            double shareAmt;
            if (i == friendCount - 1) {
                shareAmt = roundToTwoDecimals(splitInput.amount() - agg);
            } else {
                shareAmt = roundToTwoDecimals(splitInput.amount() * percentShare / totalShares);
            }

            if (friend.isSelf()) {
                selfInvolved = true;
                selfShare = shareAmt;
            } else {
                TransactionSplit split = TransactionSplit.builder()
                        .transactionId(txn.getId())
                        .friendId(friend.id())
                        .friendName(friend.name())
                        .amount(shareAmt)
                        .strategy(splitInput.strategy())
                        .build();

                splits.add(split);
            }

            agg += shareAmt;
            i++;
        }

        // Todo: following two ops should be part of txn (to be atomic) when DB is implemented.
        splitRepository.saveSplits(splits);
        double myShare = selfInvolved ? selfShare : 0.0;
        txn.setMyShare(myShare);
        transactionRepository.saveTransaction(txn);
        return new SplitResult(myShare, splits);
    }

    private SplitResult splitEqualAndSave(NormalizedTransaction txn, SplitInput splitInput) {
        int friendCount = splitInput.valuePerFriend().size();
        double perPersonShareAmt = roundToTwoDecimals(splitInput.amount() / friendCount);
        double agg = 0.0;
        int i = 0;
        List<TransactionSplit> splits = new ArrayList<>();
        boolean selfInvolved = false;
        double selfShareAmt = 0.0;

        for (Friend friend : splitInput.valuePerFriend().keySet()) {
            double shareAmt = perPersonShareAmt;
            // the last person absorbs remaining amount
            if (i == friendCount - 1) {
                shareAmt = roundToTwoDecimals(splitInput.amount() - agg);
            }
            if (friend.isSelf()) {
                selfInvolved = true;
                selfShareAmt = shareAmt;
            } else {
                TransactionSplit split = TransactionSplit.builder()
                        .transactionId(txn.getId())
                        .friendId(friend.id())
                        .friendName(friend.name())
                        .amount(shareAmt)
                        .strategy(splitInput.strategy())
                        .build();

                splits.add(split);
            }

            agg += shareAmt;
            i++;
        }

        // Todo: following two ops should be part of txn (to be atomic) when DB is implemented.
        splitRepository.saveSplits(splits);
        double myShare = selfInvolved ? selfShareAmt : 0.0;
        txn.setMyShare(myShare);
        transactionRepository.saveTransaction(txn);
        return new SplitResult(myShare, splits);
    }

    private SplitResult splitCustomAndSave(NormalizedTransaction txn, SplitInput splitInput) {
        List<TransactionSplit> splits = new ArrayList<>();
        boolean selfInvolved = false;
        double selfShareAmt = 0.0;

        for (Friend friend : splitInput.valuePerFriend().keySet()) {
            double shareAmt = splitInput.valuePerFriend().get(friend);

            if (friend.isSelf()) {
                selfInvolved = true;
                selfShareAmt = shareAmt;
            } else {
                TransactionSplit split = TransactionSplit.builder()
                        .transactionId(txn.getId())
                        .friendId(friend.id())
                        .friendName(friend.name())
                        .amount(shareAmt)
                        .strategy(splitInput.strategy())
                        .build();

                splits.add(split);
            }
        }

        // Todo: following two ops should be part of txn (to be atomic) when DB is implemented.
        splitRepository.saveSplits(splits);
        double myShare = selfInvolved ? selfShareAmt : 0.0;
        txn.setMyShare(myShare);
        transactionRepository.saveTransaction(txn);
        return new SplitResult(myShare, splits);
    }

    private void validateSplitInput(SplitInput splitInput) throws InvalidSplitException {
        if (splitInput == null) {
            throw new InvalidSplitException("input must be non-null");
        }

        if (splitInput.amount() <= 0) {
            log.warn("expected split amount > 0, found {}", splitInput.amount());
            throw new InvalidSplitException("Split amount must be greater than zero");
        }

        if (splitInput.strategy() == null) {
            log.warn("expected a non-null split strategy");
            throw new InvalidSplitException("Split strategy cannot be null");
        }

        if (splitInput.valuePerFriend() == null || splitInput.valuePerFriend().isEmpty()) {
            log.warn("expected a valid split distribution");
            throw new InvalidSplitException("Split distribution cannot be null or empty");
        }

        // User cannot split with self only
        boolean hasFriendOtherThanSelf = splitInput.valuePerFriend().keySet().stream()
                .filter(Objects::nonNull)
                .anyMatch(f -> !f.isSelf());
        if (!hasFriendOtherThanSelf) {
            throw new InvalidSplitException("At least one friend (other than self) required for split");
        }

        boolean invalidFriend = splitInput.valuePerFriend().keySet().stream().anyMatch(Objects::isNull);
        if (invalidFriend) {
            throw new InvalidSplitException("At least one friend is an invalid reference");
        }

        boolean invalidAmount = splitInput.valuePerFriend().values().stream().anyMatch(val -> val < 0);
        if (invalidAmount) {
            throw new InvalidSplitException("At least one friend has invalid split amount");
        }

        switch (splitInput.strategy()) {
            case EQUAL -> {
                // EQUAL requires no non-zero validation for the map values
            }
            case SHARE -> validateShareSplit(splitInput);
            case PERCENT -> validatePercentSplit(splitInput);
            case CUSTOM -> validateCustomSplit(splitInput);
        }
    }

    private void validateShareSplit(SplitInput splitInput) throws InvalidSplitException {
        boolean isZeroShare = splitInput.valuePerFriend().values().stream().anyMatch(val -> val == 0);
        if (isZeroShare) {
            throw new InvalidSplitException("Share must be > 0 for all friends");
        }
    }

    private void validatePercentSplit(SplitInput splitInput) throws InvalidSplitException {
        boolean isZeroPercent = splitInput.valuePerFriend().values().stream().anyMatch(val -> val == 0);
        if (isZeroPercent) {
            throw new InvalidSplitException("Percent must be > 0 for all friends");
        }

        Double percentSum = splitInput.valuePerFriend().values().stream().mapToDouble(Double::doubleValue).sum();
        if (!percentSum.equals(100.0)) {
            throw new InvalidSplitException("Aggregated percent is not 100%");
        }
    }

    private void validateCustomSplit(SplitInput splitInput) throws InvalidSplitException {
        boolean isZeroCustom = splitInput.valuePerFriend().values().stream().anyMatch(val -> val == 0);
        if (isZeroCustom) {
            throw new InvalidSplitException("Amount must be > 0 for all friends");
        }

        Double sum = splitInput.valuePerFriend().values().stream().mapToDouble(Double::doubleValue).sum();
        if (!sum.equals(splitInput.amount())) {
            throw new InvalidSplitException("Total amount does not match aggregated split amount");
        }
    }

    private static double roundToTwoDecimals(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
