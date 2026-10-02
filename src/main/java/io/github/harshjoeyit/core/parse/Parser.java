package io.github.harshjoeyit.core.parse;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.exception.UnsupportedNarrationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public interface Parser {

    List<NormalizedTransaction> parseDelimited(BufferedReader reader)
            throws IOException, IllegalArgumentException, UnsupportedNarrationException, MalformedNarrationException;
}
