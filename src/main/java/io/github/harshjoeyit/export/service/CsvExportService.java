package io.github.harshjoeyit.export.service;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.repository.StatementRepository;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import io.github.harshjoeyit.export.config.ExportConfig;
import io.github.harshjoeyit.export.model.CsvExportInput;
import io.github.harshjoeyit.export.model.ExportType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.lang.String.join;

/**
 * Fetches transactions for a statement from TransactionRepository.
 * The Config: Uses the profile from ExportConfig.
 * The Formatting: Formats cells (dates, amounts, category tags, title fallbacks).
 * The Writer: Uses Apache Commons CSV (CSVPrinter) to write files into the target output directory.
 */
@Slf4j
@Service
public class CsvExportService {
    private final ExportConfig exportConfig;
    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;

    private static final String DEFAULT_EXPORT_DIR = "./export";
    private static final String DEFAULT_DATE_FORMAT = "dd/MM/yy";

    // needs default profile in export-profile
    private static final String DEFAULT_PROFILE = "default";

    public CsvExportService(ExportConfig exportConfig,
                            StatementRepository statementRepository,
                            TransactionRepository transactionRepository
    ) {
        this.exportConfig = exportConfig;
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
    }

    public void export(CsvExportInput csvExportInput, String profile, String outputDir) throws IllegalArgumentException {
        if (csvExportInput.statementId() == null
                && (csvExportInput.periodStartDate() == null || csvExportInput.periodEndDate() == null)) {
            throw new IllegalArgumentException("Either statement id of period start and end date required for export");
        }

        String profileToUse = profile != null && !profile.isBlank() ? profile : DEFAULT_PROFILE;
        if (profileToUse.equals(DEFAULT_PROFILE)) {
            log.warn("Using {} profile", DEFAULT_PROFILE);
        }

        // Retrieve profile
        ExportConfig.Profile exportProfile = exportConfig.profiles() != null
                ? exportConfig.profiles().get(profileToUse)
                : null;
        if (exportProfile == null) {
            throw new IllegalArgumentException("Export profile not found: " + profileToUse);
        }
        DateTimeFormatter dateTimeFormatter = exportProfile.dateFormat() != null
                && !exportProfile.dateFormat().isBlank()
                ? DateTimeFormatter.ofPattern(exportProfile.dateFormat())
                : DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);

        // Fetch transactions
        List<NormalizedTransaction> transactions = findTransactions(csvExportInput);
        String outputDirToUse = outputDir != null && !outputDir.isBlank() ? outputDir : DEFAULT_EXPORT_DIR;

        if (exportProfile.isMulti()) {
            writeFile(outputDirToUse, ExportType.PURCHASE.toString(),
                    exportProfile.multi().purchase(),
                    getFilteredTransactions(transactions, ExportType.PURCHASE), exportProfile.categoryMapping(),
                    true, dateTimeFormatter);
            writeFile(outputDirToUse, ExportType.INCOME.toString(),
                    exportProfile.multi().income(),
                    getFilteredTransactions(transactions, ExportType.INCOME), exportProfile.categoryMapping(),
                    true, dateTimeFormatter);
            writeFile(outputDirToUse, ExportType.INVEST.toString(),
                    exportProfile.multi().investment(),
                    getFilteredTransactions(transactions, ExportType.INVEST), exportProfile.categoryMapping(),
                    true, dateTimeFormatter);
            if (exportProfile.multi().lendOrBorrowed() != null) {
                writeFile(outputDirToUse, ExportType.LEND_OR_BORROWED.toString(),
                        exportProfile.multi().lendOrBorrowed(),
                        getFilteredTransactions(transactions, ExportType.LEND_OR_BORROWED), exportProfile.categoryMapping(),
                        true, dateTimeFormatter);
            }
        } else {
            writeFile(outputDirToUse, ExportType.CONSOLIDATED.toString(),
                    exportProfile.single(),
                    transactions, exportProfile.categoryMapping(),
                    false, dateTimeFormatter);
        }
    }

    // Find the transactions per provided export input
    private List<NormalizedTransaction> findTransactions(CsvExportInput csvExportInput) {
        List<NormalizedTransaction> transactions = new ArrayList<>();
        if (csvExportInput.statementId() != null) {
            transactions = transactionRepository.findReviewedTransactionsByStatementId(csvExportInput.statementId());
        } else if (csvExportInput.periodStartDate() != null && csvExportInput.periodEndDate() != null) {
            // find all the transactions from all the statements which fall in this date range
            List<Statement> statements = statementRepository.findAll();
            for (Statement st : statements) {
                List<NormalizedTransaction> filteredTxns = transactionRepository.findReviewedTransactionsByStatementId(st.id())
                        .stream()
                        .filter(txn ->
                                txn.getDatetime().toLocalDate().isAfter(csvExportInput.periodStartDate().minusDays(1))
                                && txn.getDatetime().toLocalDate().isBefore(csvExportInput.periodEndDate().plusDays(1))
                        ).toList();

                transactions.addAll(filteredTxns);
                log.debug("Added {} txn from statement: {}", filteredTxns.size(), st.id());
            }
        }

        return transactions;
    }

    // Filter and return transactions based on export type
    private List<NormalizedTransaction> getFilteredTransactions(
            List <NormalizedTransaction> transactions, ExportType exportType) {
        return transactions
                .stream()
                .filter(txn -> exportType.getTxnTypes().contains(txn.getType()))
                .toList();
    }

    // Write to the export file
    private void writeFile(String outputDir, String fileNamePrefix, ExportConfig.ColumnMappingConfig columnMappingConfig,
                             List<NormalizedTransaction> transactions, Map<TxnCategory, String> categoryMapping,
                             boolean useAbsAmount, DateTimeFormatter dateTimeFormatter) {

        if (transactions.isEmpty()) {
            log.warn("No {} transactions found", fileNamePrefix);
            return;
        }

        Path outputFilePath = createFile(fileNamePrefix, outputDir);
        String []header = columnMappingConfig.columns().keySet().toArray(new String[0]);

        // Configure the CSV format and define column headers
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(header)
                .get();

        // Open BufferedWriter and CSVPrinter
        try (
                BufferedWriter writer = Files.newBufferedWriter(outputFilePath);
                CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)
        ) {
            for (NormalizedTransaction txn : transactions) {
                List<String> row = new ArrayList<>();

                for (String token : columnMappingConfig.columns().values()) {
                    row.add(getTransactionDataByToken(token, txn, categoryMapping, useAbsAmount, dateTimeFormatter));
                }
                // Write the row
                csvPrinter.printRecord(row);
            }

            log.info("CSV file saved successfully at: {}", outputFilePath);
        } catch (IOException e) {
            log.error("Failed to write to csv file: {}", outputFilePath, e);
        }
    }

    // Create export file
    private Path createFile(String filePrefix, String outputDir) {
        String filename = String.join("_", List.of(
                filePrefix,
                String.valueOf(LocalDateTime.now().toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()))
        ));
        Path filePath = Path.of(outputDir, filename + ".csv");

        try {
            // Create parent directories if they don't exist yet
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }

            // Check if the file already exists before creating it
            if (!Files.exists(filePath)) {
                return Files.createFile(filePath);
            }
        } catch (IOException e) {
            log.error("unable to create file", e);
        }

        // Returns the existing file path
        return filePath;
    }

    private String getTransactionDataByToken(
            String token, NormalizedTransaction txn,
            Map<TxnCategory, String> categoryMapping,
            boolean useAbsAmount, DateTimeFormatter dateTimeFormatter
    ) throws UnsupportedOperationException {
        return switch (token.toLowerCase()) {
            case "title" -> getTxnTitle(txn);
            case "amount" -> String.valueOf(getTxnAmount(txn, useAbsAmount));
            case "date" -> txn.getDatetime() != null ? dateTimeFormatter.format(txn.getDatetime()) : "";
            case "category" -> getUserDefinedCategory(categoryMapping, txn.getCategory());
            case "source" -> txn.getCounterparty() != null ? txn.getCounterparty() : "";
            case "id" -> txn.getId() != null ? txn.getId().toString() : "";
            case "direction" -> txn.getDirection() != null ? txn.getDirection().name() : "";
            case "instrument" -> txn.getInstrumentId() != null ? txn.getInstrumentId() : "";
            case "statement_id" -> txn.getStatementId() != null ? txn.getStatementId().toString() : "";
            case "remarks" -> txn.getReviewRemarks() != null ? txn.getReviewRemarks() : "";
            case "reference" -> txn.getBankReference() != null ? txn.getBankReference() : "";
            case "mode" -> txn.getMode() != null ? txn.getMode().name() : "";
            case "type" -> txn.getType() != null ? txn.getType().name() : "";
            default -> throw new UnsupportedOperationException("Field token '" + token + "' is not supported");
        };
    }

    private String getTxnTitle(NormalizedTransaction txn) {
        if (txn.getReviewRemarks() != null && !txn.getReviewRemarks().isBlank()) {
            return txn.getReviewRemarks();
        }
        if (txn.getUserComment() != null && !txn.getUserComment().isBlank()) {
            return txn.getUserComment();
        }
        String counterparty = txn.getCounterparty() != null ? txn.getCounterparty() : "";
        String counterpartyId = txn.getCounterpartyId() != null && !txn.getCounterpartyId().isBlank()
                ? " | " + txn.getCounterpartyId()
                : "";
        String result = (counterparty + counterpartyId).trim();
        return result.isBlank() ? "Unknown" : result;
    }

    private String getUserDefinedCategory(Map<TxnCategory, String> categoryMapping, TxnCategory category) {
        if (category == null) {
            log.warn("Invalid category: {}", category);
            return "";
        }
        if (categoryMapping == null || categoryMapping.isEmpty()
                || categoryMapping.get(category) == null || categoryMapping.get(category).isEmpty()) {
            return category.toString();
        }
        return categoryMapping.get(category);
    }

    private double getTxnAmount(NormalizedTransaction txn, boolean useAbsAmount) {
        if (useAbsAmount) {
            return txn.getAmount(); // txn always stores absolute amount
        }

        return txn.getDirection().equals(TxnDirection.CREDIT)
                  ? txn.getAmount()
                  : -1 * txn.getAmount();
    }
}
