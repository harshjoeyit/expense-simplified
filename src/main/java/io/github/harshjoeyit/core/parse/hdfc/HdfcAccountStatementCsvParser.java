package io.github.harshjoeyit.core.parse.hdfc;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.parse.Parser;
import io.github.harshjoeyit.core.model.RawCsvRow;
import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.exception.UnsupportedNarrationException;
import io.github.harshjoeyit.core.parse.hdfc.narration.NarrationParser;
import io.github.harshjoeyit.core.parse.model.NarrationParseResult;

public class HdfcAccountStatementCsvParser extends Parser {

    public NormalizedTransaction parse(RawCsvRow txnRow) throws UnsupportedNarrationException, MalformedNarrationException {
        // Todo: parse the raw csv row to find - date, narration, credit/debit, amount
        String narration = "";
        NarrationParser narrationParserToUse = null;

        for(NarrationParser np : narrationParsers) {
            if (np.canParse(narration)) {
                narrationParserToUse = np;
                break;
            }
        }

        if (narrationParserToUse == null) {
            throw new UnsupportedNarrationException("Could not parse: " + narration);
        }

        NarrationParseResult narrationParserResult = narrationParserToUse.parse(narration);

        return NormalizedTransaction.builder().build();
    }
}
