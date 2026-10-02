package io.github.harshjoeyit.core.parse;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.exception.UnsupportedNarrationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public interface Parser {

    /**
     * Parses delimited statement input into a list (CSVs, other custom
     * delimited lists) of normalized transactions. All parsed transactions
     * must have absolute (non-negative) amount values, with flow direction
     * denoted solely by their {@code TxnDirection} field.
     */
    List<NormalizedTransaction> parseDelimited(BufferedReader reader)
            throws IOException, IllegalArgumentException, UnsupportedNarrationException, MalformedNarrationException;
}
