package io.github.harshjoeyit.model;

import io.github.harshjoeyit.core.dedup.DeduplicationResult;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;

import java.util.List;

public record StatementImportResult(
        /**
         * New Statement object for the imported file.
         * null if imported statement is 100% duplicate or cross-instrument collision
         */
        Statement statement,
        DeduplicationResult dedupResult
) {
    public List<NormalizedTransaction> newTransactions() {
        return dedupResult != null && dedupResult.newTransactions() != null
                ? dedupResult.newTransactions()
                : List.of();
    }
}

