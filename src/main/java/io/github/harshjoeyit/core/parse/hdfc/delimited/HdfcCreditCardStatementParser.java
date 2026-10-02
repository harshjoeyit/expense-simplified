package io.github.harshjoeyit.core.parse.hdfc.delimited;

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
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Format: docs/statement-formats/HDFC_CC.txt
 */
@Slf4j
@Service
public class HdfcCreditCardStatementParser implements Parser {

    private static final String CUSTOM_DELIMITER = "~|~";
    private static final Set<String> EXPECTED_STMT_HEADER = Set.of(
            "Transaction type", "Primary / Addon Customer Name",
            "DATE", "Description", "AMT", "Debit /Credit", "REWARDS"
    );
    private static final String TARGET_HEADER_PREFIX = "Transaction type~|~Primary / Addon Customer Name";
    private static final String DATETIME_FORMAT = "dd/MM/yyyy HH:mm:ss";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_FORMAT);
    private static final Set<String> VALID_TRANSACTION_TYPES = Set.of("Domestic", "International");
    private static final Set<String> ELIGIBLE_CC_PAYMENT_DESCRIPTION = Set.of("CC PAYMENT", "CREDIT CARD PAYMENT");

    @Override
    public List<NormalizedTransaction> parseDelimited(BufferedReader reader)
            throws IOException, IllegalArgumentException, UnsupportedNarrationException, MalformedNarrationException {

        // Since the statement CSV has a few more details before and after list of transaction
        // Hence, skip metadata until we find the exact header row
        String headerLine;
        boolean headerFound = false;

        while ((headerLine = reader.readLine()) != null) {
            if (headerLine.startsWith(TARGET_HEADER_PREFIX)) {
                headerFound = true;
                break;
            }
        }

        if (!headerFound) {
            throw new MalformedCsvException("Invalid CSV format. "
                    + "Transaction table header not found. Expected: "
                    + TARGET_HEADER_PREFIX);
        }

        CSVFormat headerFormat = CSVFormat.DEFAULT.builder()
                .setDelimiter(CUSTOM_DELIMITER)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .get();

        // HDFC statement CSV header rows end with a trailing delimiter (~|~), which creates an empty trailing column.
        List<String> headerNames = new ArrayList<>();
        try (CSVParser headerParser = headerFormat.parse(new StringReader(headerLine))) {
            for (CSVRecord record : headerParser) {
                for (String val : record) {
                    // skip empty header
                    if (val != null && !val.isBlank()) {
                        headerNames.add(val.trim());
                    }
                }
                break;
            }
        }

        Set<String> actualHeaders = Set.copyOf(headerNames);
        if (!actualHeaders.equals(EXPECTED_STMT_HEADER)) {
            throw new MalformedCsvException(
                    "Unexpected CSV headers: " + actualHeaders
                            + ", Expected CSV headers: " + EXPECTED_STMT_HEADER
            );
        }

        // Pass the remaining reader stream directly CSV reader
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
                // If a row does not match the transaction structure or has an invalid transaction type,
                // the transaction section has ended.
                if (record.size() < EXPECTED_STMT_HEADER.size()
                        || !VALID_TRANSACTION_TYPES.contains(record.get("Transaction type").trim())) {
                    break;
                }

                log.debug("Processing HDFC CC record [line {}]: {}", record.getRecordNumber(), record);

                try {
                    // Map fields safely using column names
                    String description = record.get("Description");
                    String parsedAmount = record.get("AMT");
                    String debitCredit = record.get("Debit /Credit");
                    String parsedDateTime = record.get("DATE");

                    // Convert extracted fields into required format
                    double amount = Math.abs(ParseUtil.parseAmount(parsedAmount));
                    LocalDateTime dateTime = LocalDateTime.parse(parsedDateTime, DATE_FORMATTER);
                    TxnDirection direction = debitCredit.equals("Cr") ? TxnDirection.CREDIT : TxnDirection.DEBIT;
                    TxnType type = resolveTxnType(description, direction);
                    CounterpartyType counterpartyType = resolveCounterPartyType(type);

                    // Depend on mapping - category, counterpartyId, instrumentId
                    NormalizedTransaction txn = NormalizedTransaction.builder()
                            .datetime(dateTime)
                            .valueDatetime(dateTime)
                            .amount(amount)
                            .direction(direction)
                            .type(type)
                            .mode(TxnMode.CREDIT_CARD)
                            .counterparty(description)
                            .counterpartyType(counterpartyType)
                            .userComment(null)
                            .bankReference(null)
                            .reviewStatus(TxnReviewStatus.PENDING)          // Default = Pending
                            .rawTxn(record.toString())
                            .build();

                    transactions.add(txn);
                } catch (Exception e) {
                    log.warn("Skipping malformed HDFC CC record [line {}]: {}. Raw record: {}",
                            record.getRecordNumber(), e.getMessage(), record);
                }
            }
        }

        log.info("Successfully parsed {} transactions from HDFC credit card statement", transactions.size());
        return transactions;
    }

    private TxnType resolveTxnType(String txnDescription, TxnDirection direction) {
        if (isCreditCardBillPayment(txnDescription)) {
            return TxnType.CREDIT_CARD_BILL_PAYMENT;
        } else if (direction == TxnDirection.CREDIT) {
            return TxnType.REFUND;
        }

        return TxnType.PURCHASE;
    }

    private CounterpartyType resolveCounterPartyType(TxnType txnType) {
        return txnType == TxnType.CREDIT_CARD_BILL_PAYMENT
                ? CounterpartyType.BANK : CounterpartyType.MERCHANT;
    }

    // Credit card payment is a line-item (transaction) in HDFC credit card statement
    private boolean isCreditCardBillPayment(String txnDescription) {
        for (String ed : ELIGIBLE_CC_PAYMENT_DESCRIPTION) {
            if (txnDescription.contains(ed)) {
                return true;
            }
        }
        return false;
    }
}