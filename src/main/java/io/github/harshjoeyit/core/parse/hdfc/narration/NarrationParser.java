package io.github.harshjoeyit.core.parse.hdfc.narration;

import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.model.NarrationParseResult;

public interface NarrationParser {
    boolean canParse(String narration);
    NarrationParseResult parse(String narration) throws MalformedNarrationException;
}
