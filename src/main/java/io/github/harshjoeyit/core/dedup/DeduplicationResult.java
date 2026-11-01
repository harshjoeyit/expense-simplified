package io.github.harshjoeyit.core.dedup;

import io.github.harshjoeyit.core.model.NormalizedTransaction;

import java.util.List;

public record DeduplicationResult(
        /**
         * transactions which are new and should be recorded
         */
        List<NormalizedTransaction> newTransactions,

        /**
         * duplicate transactions mapped with corresponding existing transaction
         */
        List<DuplicateMatch> duplicates,

        /**
         * statements where existing transactions were found
         */
        List<StatementOverlapSummary> statementSummaries,

        /**
         *  instrument Id under which all the incoming transactions were found to be existing
         */
        String crossInstrumentCollision
) {
    public boolean isFullDuplicate() {
        return (newTransactions == null || newTransactions.isEmpty())
                && duplicates != null && !duplicates.isEmpty();
    }

    public boolean hasCrossInstrumentCollision() {
        return crossInstrumentCollision != null && !crossInstrumentCollision.isBlank();
    }
}
