package io.github.harshjoeyit.core.parse;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.RawCsvRow;
import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.exception.UnsupportedNarrationException;
import io.github.harshjoeyit.core.parse.hdfc.narration.NarrationParser;
import io.github.harshjoeyit.core.parse.model.TxnDirection;

import java.time.LocalDate;
import java.util.Set;

/**
 * date
 * valueDate
 * amount
 * direction
 * instrumentId
 * rawRow
 */
public abstract class Parser {
    protected LocalDate txnDate;
    protected LocalDate valueDate;
    protected Double amount;
    protected TxnDirection paymentDirection;
    protected String instrumentId;      // Bank Account / Credit card Identifier
    protected String rawTxnRow;
    protected Set<NarrationParser> narrationParsers;

    abstract public NormalizedTransaction parse(RawCsvRow txnRow) throws UnsupportedNarrationException, MalformedNarrationException;
}
