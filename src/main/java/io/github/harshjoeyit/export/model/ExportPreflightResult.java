package io.github.harshjoeyit.export.model;

import java.util.Map;

public record ExportPreflightResult(
        int eligibleTxns,
        Map<ExportExclusionReason, Integer> excludedTxns
) {
    public ExportPreflightResult {
        excludedTxns = excludedTxns == null ? Map.of() : Map.copyOf(excludedTxns);
    }

    public int totalCount() {
        return eligibleTxns + excludedTxnsCount();
    }

    public int excludedTxnsCount() {
        return excludedTxns.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean hasExcluded() {
        return excludedTxnsCount() > 0;
    }

    public int excludedCountByReason(ExportExclusionReason exportExclusionReason) {
        return excludedTxns.getOrDefault(exportExclusionReason, 0);
    }
}

