package io.github.harshjoeyit.core.dedup;

import java.time.LocalDate;
import java.util.UUID;

public record StatementOverlapSummary(
        UUID statementId,
        String instrumentId,
        /**
         * Number of duplicate transactions recorded in this statement which match with incoming transactions
         */
        int duplicateCount,
        LocalDate minDate,
        LocalDate maxDate
) {}
