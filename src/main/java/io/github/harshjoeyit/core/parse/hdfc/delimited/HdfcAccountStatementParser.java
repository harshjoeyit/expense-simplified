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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
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
    private static final Set<String> EXPECTED_STMT_HEADER_ALT = Set.of(
        "Date", "Narration", "Value Date", "Debit Amount", "Credit Amount", "Chq/Ref Number", "Closing Balance"
    );
    private static final String DATE_PATTERN = "[dd/MM/yyyy][dd/MM/yy][dd-MM-yyyy][dd-MM-yy]";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);
    Set<String> NEFT_NARRAIION_PREFIXES = Set.of("NEFT", "NET-NEFT", "IB-NEFT", "MB-NEFT");


    private final Set<NarrationParser> narrationParsers;

    public HdfcAccountStatementParser(Set<NarrationParser> narrationParsers) {
        this.narrationParsers = narrationParsers != null ? narrationParsers : Collections.emptySet();
    }

    @Override
    public List<NormalizedTransaction> parseDelimited(BufferedReader reader)
            throws IOException, IllegalArgumentException, UnsupportedNarrationException, MalformedNarrationException {

        // Scan past any leading account metadata rows until the table header row is found
        String headerLine = null;
        boolean headerFound = false;

        while ((headerLine = reader.readLine()) != null) {
            String trimmed = headerLine.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("date") || (trimmed.contains("date") && trimmed.contains("narration"))) {
                headerFound = true;
                break;
            }
        }

        if (!headerFound) {
            throw new MalformedCsvException("Invalid CSV format. Transaction table header not found.");
        }

        CSVFormat headerFormat = CSVFormat.DEFAULT.builder()
                .setDelimiter(CUSTOM_DELIMITER)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .get();

        List<String> headerNames = new ArrayList<>();
        try (CSVParser headerParser = headerFormat.parse(new StringReader(headerLine))) {
            for (CSVRecord record : headerParser) {
                for (String val : record) {
                    if (val != null && !val.isBlank()) {
                        headerNames.add(val.trim());
                    }
                }
                break;
            }
        }

        Set<String> actualHeaders = Set.copyOf(headerNames);
        if (!ParseUtil.matchesHeaders(actualHeaders, EXPECTED_STMT_HEADER)
                && !ParseUtil.matchesHeaders(actualHeaders, EXPECTED_STMT_HEADER_ALT)) {
            log.error("CSV header mismatch. Actual: {}, Expected: {} or {}", actualHeaders, EXPECTED_STMT_HEADER, EXPECTED_STMT_HEADER_ALT);
            throw new MalformedCsvException(
                    "Unexpected CSV headers: " + actualHeaders
                            + ", Expected CSV headers: " + EXPECTED_STMT_HEADER
            );
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(CUSTOM_DELIMITER)
                .setHeader(headerNames.toArray(new String[0]))
                .setSkipHeaderRecord(false)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .get();

        List<NormalizedTransaction> transactions = new ArrayList<>();

        try (CSVParser csvParser = format.parse(reader)) {
            for (CSVRecord record : csvParser) {
                log.debug("Processing record [line {}]: {}", record.getRecordNumber(), record);

                try {
                    String parsedDate = ParseUtil.getField(record, "Date");
                    if (parsedDate == null || parsedDate.isBlank()) {
                        continue;
                    }
                    String parsedValueDate = ParseUtil.getField(record, "Value Date");
                    if (parsedValueDate == null) {
                        parsedValueDate = ParseUtil.getField(record, "Value Dat");
                    }
                    String parsedNarration = ParseUtil.getField(record, "Narration");
                    String parsedDebitAmount = ParseUtil.getField(record, "Debit Amount");
                    String parsedCreditAmount = ParseUtil.getField(record, "Credit Amount");
                    String refNumber = ParseUtil.getField(record, "Chq/Ref Number");
                    String closingBalance = ParseUtil.getField(record, "Closing Balance");

                    double debitAmount = Math.abs(ParseUtil.parseAmount(parsedDebitAmount));
                    double creditAmount = Math.abs(ParseUtil.parseAmount(parsedCreditAmount));
                    TxnDirection direction = resolveTxnDirection(debitAmount, creditAmount);
                    double amount = debitAmount > 0 ? debitAmount : creditAmount;

                    // time unavailable in account statement, defaulting to start of day
                    LocalDateTime datetime = LocalDate.parse(parsedDate.trim(), DATE_FORMATTER).atStartOfDay();
                    LocalDateTime valueDateTime = (parsedValueDate != null && !parsedValueDate.isBlank())
                            ? LocalDate.parse(parsedValueDate.trim(), DATE_FORMATTER).atStartOfDay()
                            : datetime;

                    NarrationParseResult narrationParserResult = parseNarrationSafely(parsedNarration, refNumber);
                    LocalDateTime crTime = LocalDateTime.now();

                    // Depend on mapping - category, counterpartyId, instrumentId
                    NormalizedTransaction txn = NormalizedTransaction.builder()
                            .datetime(datetime)
                            .valueDatetime(valueDateTime)
                            .amount(amount)
                            .direction(direction)
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
                            .createdAt(crTime)
                            .updatedAt(crTime)
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

    private NarrationParseResult parseNarrationSafely(String narration, String refNumber) {
        if (narration == null || narration.isBlank()) {
            return NarrationParseResult.builder()
                    .bankReference(refNumber != null ? refNumber.trim() : null)
                    .build();
        }

        String trimmedNarration = narration.trim();

        for (NarrationParser parser : narrationParsers) {
            if (parser.canParse(trimmedNarration)) {
                try {
                    return parser.parse(trimmedNarration);
                } catch (Exception e) {
                    log.warn("Failed to parse narration using {}: {}. Falling back to default narration handling.",
                            parser.getClass().getSimpleName(), e.getMessage());
                    break;
                }
            }
        }

        // Fallback: Infer basic mode / narration type if recognizable, set counterparty to narration text
        TxnMode mode = inferModeFromNarration(trimmedNarration);
        NarrationType narrationType = inferNarrationType(trimmedNarration);

        return NarrationParseResult.builder()
                .narrationType(narrationType)
                .txnMode(mode)
                .counterparty(trimmedNarration)
                .bankReference(refNumber != null ? refNumber.trim() : null)
                .build();
    }

    private TxnMode inferModeFromNarration(String narration) {
        String upper = narration.toUpperCase(Locale.ROOT);

        if (NEFT_NARRAIION_PREFIXES.stream().anyMatch(upper::startsWith)
                || upper.startsWith("RTGS")
                || upper.startsWith("FT-")) {
            return TxnMode.FT;
        } else if (upper.startsWith("TP-")) {
            return TxnMode.TP;
        } else if (upper.startsWith("A2A")) {
            return TxnMode.A2A;
        }
        return null;
    }

    private NarrationType inferNarrationType(String narration) {
        String upper = narration.toUpperCase(Locale.ROOT);
        if (upper.contains("SWEEP IN") || upper.contains("SWEEP-IN")) {
            return NarrationType.SWEEP_IN;
        } else if (upper.contains("INTEREST") || upper.startsWith("INT.")) {
            return NarrationType.INTEREST;
        } else if (NEFT_NARRAIION_PREFIXES.stream().anyMatch(upper::startsWith)
                || upper.startsWith("RTGS")
                || upper.startsWith("FT-")) {
            return NarrationType.FT;
        } else if (upper.startsWith("TP-")) {
            return NarrationType.TP;
        } else if (upper.startsWith("A2A")) {
            return NarrationType.A2A;
        }
        return null;
    }
}
