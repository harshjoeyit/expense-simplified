package io.github.harshjoeyit.split.model;

import java.util.List;

public record SplitResult(
        double myShare,
        List<TransactionSplit> splits
) {}
