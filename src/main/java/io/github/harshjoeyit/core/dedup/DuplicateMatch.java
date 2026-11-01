package io.github.harshjoeyit.core.dedup;

import io.github.harshjoeyit.core.model.NormalizedTransaction;

public record DuplicateMatch(
        NormalizedTransaction incoming,     // new transactions, which is duplicate of existing transaction
        NormalizedTransaction existing
) {}
