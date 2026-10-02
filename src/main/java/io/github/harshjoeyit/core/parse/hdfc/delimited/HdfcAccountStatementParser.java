package io.github.harshjoeyit.core.parse.hdfc.delimited;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.Parser;
import io.github.harshjoeyit.core.parse.exception.MalformedCsvException;
import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.exception.UnsupportedNarrationException;
import io.github.harshjoeyit.core.parse.hdfc.narration.NarrationParser;
import io.github.harshjoeyit.core.parse.model.*;
import io.github.harshjoeyit.core.parse.util.ParseUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class HdfcAccountStatementParser implements Parser {

    private static final String CUSTOM_DELIMITER = ",";
    private static final Set<String> EXPECTED_STMT_HEADER = Set.of(
        "Date", "Narration", "Value Dat", "Debit Amount", "Credit Amount", "Chq/Ref Number", "Closing Balance"
    );
    private static final String DATE_FORMAT = "dd/MM/yy";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);

    private final Set<NarrationParser> narrationParsers;

    public HdfcAccountStatementParser() {
        this.narrationParsers = Collections.emptySet();
    }

    public HdfcAccountStatementParser(Set<NarrationParser> narrationParsers) {
        this.narrationParsers = narrationParsers != null ? narrationParsers : Collections.emptySet();
    }

    @Override
    public List<NormalizedTransaction> parseDelimited(BufferedReader reader)
            throws IOException, IllegalArgumentException, UnsupportedNarrationException, MalformedNarrationException {

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(CUSTOM_DELIMITER)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .get();

        List<NormalizedTransaction> transactions = new ArrayList<>();

        try (CSVParser csvParser = format.parse(reader)) {
            Set<String> actualHeaders = csvParser.getHeaderMap().keySet();

            if (!actualHeaders.equals(EXPECTED_STMT_HEADER)) {
                log.error("CSV header mismatch. Actual: {}, Expected: {}", actualHeaders, EXPECTED_STMT_HEADER);
                throw new MalformedCsvException(
                        "Unexpected CSV headers: " + actualHeaders
                                + ", Expected CSV headers: " + EXPECTED_STMT_HEADER
                );
            }

            for (CSVRecord record : csvParser) {
                log.debug("Processing record [line {}]: {}", record.getRecordNumber(), record);

                try {
                    String parsedDate = record.get("Date");
                    String parsedValueDate = record.get("Value Dat");
                    String parsedNarration = record.get("Narration");
                    String parsedDebitAmount = record.get("Debit Amount");
                    String parsedCreditAmount = record.get("Credit Amount");
                    String refNumber = record.get("Chq/Ref Number");
                    String closingBalance = record.get("Closing Balance");

                    double debitAmount = Math.abs(ParseUtil.parseAmount(parsedDebitAmount));
                    double creditAmount = Math.abs(ParseUtil.parseAmount(parsedCreditAmount));
                    TxnDirection direction = resolveTxnDirection(debitAmount, creditAmount);
                    double amount = debitAmount > 0 ? debitAmount : creditAmount;

                    // time unavailable in account statement, defaulting to start of day
                    LocalDateTime datetime = LocalDate.parse(parsedDate.trim(), DATE_FORMATTER).atStartOfDay();
                    LocalDateTime valueDateTime = LocalDate.parse(parsedValueDate.trim(), DATE_FORMATTER).atStartOfDay();

                    NarrationParseResult narrationParserResult = getNarrationParser(parsedNarration).parse(parsedNarration);

                    // Depend on mapping - category, counterpartyId, instrumentId
                    NormalizedTransaction txn = NormalizedTransaction.builder()
                            .datetime(datetime)
                            .valueDatetime(valueDateTime)
                            .amount(amount)
                            .direction(direction)
                            .type(TxnType.UNKNOWN)
                            .mode(narrationParserResult.getTxnMode())
                            .counterpartyId(narrationParserResult.getCounterpartyId())
                            .counterparty(narrationParserResult.getCounterparty())
                            .counterpartyType(narrationParserResult.getCounterpartyType())
                            .userComment(narrationParserResult.getUserComment())
                            .bankReference(narrationParserResult.getBankReference() != null
                                    ? narrationParserResult.getBankReference()
                                    : (refNumber != null ? refNumber.trim() : null))
                            .reviewStatus(TxnReviewStatus.PENDING)                          // Default = Pending
                            .rawTxn(record.toString())
                            .build();

                    transactions.add(txn);
                } catch (UnsupportedNarrationException | MalformedNarrationException e) {
                    log.warn("Skipping record [line {}] due to narration issue: {}. Raw record: {}",
                            record.getRecordNumber(), e.getMessage(), record);
                } catch (Exception e) {
                    log.warn("Skipping malformed record [line {}]: {}. Raw record: {}",
                            record.getRecordNumber(), e.getMessage(), record);
                }
            }
        }

        log.info("Successfully parsed {} transactions from statement", transactions.size());
        return transactions;
    }

    private TxnDirection resolveTxnDirection(double debitAmount, double creditAmount) {
        if (debitAmount > 0 && creditAmount == 0) {
            return TxnDirection.DEBIT;
        } else if (debitAmount == 0 && creditAmount > 0) {
            return TxnDirection.CREDIT;
        }

        throw new MalformedCsvException("Invalid transaction amounts - debit: "
                + debitAmount + "; credit: " + creditAmount);
    }

    private NarrationParser getNarrationParser(String narration) {
        return narrationParsers.stream()
                .filter(np -> np.canParse(narration))
                .findFirst()
                .orElseThrow(() -> new UnsupportedNarrationException("Could not parse: " + narration));
    }
}
