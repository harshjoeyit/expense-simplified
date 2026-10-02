package io.github.harshjoeyit.core.parse.scapia.delimited;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.Parser;
import io.github.harshjoeyit.core.parse.exception.MalformedCsvException;
import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.exception.UnsupportedNarrationException;
import io.github.harshjoeyit.core.parse.model.CounterpartyType;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Statement format: docs/statement-formats/Scapia_CC.txt
 */
@Slf4j
@Service
public class ScapiaCreditCardStatementParser implements Parser {

    private static final String CUSTOM_DELIMITER = ",";
    private static final Set<String> EXPECTED_STMT_HEADER = Set.of(
            "date", "time", "description", "amount"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

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
                log.debug("Processing Scapia CC record [line {}]: {}", record.getRecordNumber(), record);

                try {
                    // Access fields by their header names
                    String parsedDescription = record.get("description");
                    String parsedAmount = record.get("amount");
                    LocalDate date = LocalDate.parse(record.get("date"), DATE_FORMATTER);
                    LocalTime time = LocalTime.parse(record.get("time"), TIME_FORMATTER);

                    // Convert extracted fields into required format
                    double rawAmount = ParseUtil.parseAmount(parsedAmount);
                    LocalDateTime dateTime = LocalDateTime.of(date, time);

                    // Derived fields
                    TxnType type = rawAmount < 0 ? TxnType.PURCHASE : TxnType.REFUND;
                    TxnDirection direction = rawAmount < 0 ? TxnDirection.DEBIT : TxnDirection.CREDIT;
                    double amount = Math.abs(rawAmount);

                    // Depend on mapping - category, counterpartyId, instrumentId
                    NormalizedTransaction txn = NormalizedTransaction.builder()
                            .datetime(dateTime)
                            .valueDatetime(dateTime)
                            .amount(amount)
                            .direction(direction)
                            .type(type)
                            .mode(TxnMode.CREDIT_CARD)
                            .counterparty(parsedDescription)
                            .counterpartyType(CounterpartyType.MERCHANT)    // CC can be used for Merchants only
                            .userComment(null)
                            .bankReference(null)
                            .reviewStatus(TxnReviewStatus.PENDING)          // Default = Pending
                            .rawTxn(record.toString())
                            .build();

                    transactions.add(txn);
                } catch (Exception e) {
                    log.warn("Skipping malformed Scapia CC record [line {}]: {}. Raw record: {}",
                            record.getRecordNumber(), e.getMessage(), record);
                }
            }
        }

        log.info("Successfully parsed {} transactions from Scapia credit card statement", transactions.size());
        return transactions;
    }
}
