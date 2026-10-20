package io.github.harshjoeyit.export.service;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
import io.github.harshjoeyit.core.repository.InMemoryStatementRepository;
import io.github.harshjoeyit.core.repository.InMemoryTransactionRepository;
import io.github.harshjoeyit.export.config.ExportConfig;
import io.github.harshjoeyit.export.model.CsvExportInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvExportServiceTest {

    private InMemoryStatementRepository statementRepository;
    private InMemoryTransactionRepository transactionRepository;
    private ExportConfig exportConfig;
    private CsvExportService exportService;

    @TempDir
    Path tempDir;

    private UUID statementId;

    @BeforeEach
    void setUp() {
        statementRepository = new InMemoryStatementRepository();
        transactionRepository = new InMemoryTransactionRepository();

        // 1. Setup multi-tracker profile ('default')
        LinkedHashMap<String, String> purchaseCols = new LinkedHashMap<>();
        purchaseCols.put("Title", "title");
        purchaseCols.put("Amount", "amount");
        purchaseCols.put("Date", "date");
        purchaseCols.put("Tags", "category");
        purchaseCols.put("Txn Id", "id");

        LinkedHashMap<String, String> incomeCols = new LinkedHashMap<>();
        incomeCols.put("Title", "title");
        incomeCols.put("Date", "date");
        incomeCols.put("Amount", "amount");
        incomeCols.put("Source", "source");
        incomeCols.put("Txn Id", "id");

        LinkedHashMap<String, String> investCols = new LinkedHashMap<>();
        investCols.put("Title", "title");
        investCols.put("Amount", "amount");
        investCols.put("Tags", "source");
        investCols.put("Last investment on", "date");

        LinkedHashMap<String, String> lendCols = new LinkedHashMap<>();
        lendCols.put("Title", "title");
        lendCols.put("Amount", "amount");
        lendCols.put("Date", "date");
        lendCols.put("Source", "source");
        lendCols.put("Type", "type");
        lendCols.put("Txn Id", "id");

        ExportConfig.MultiFileColumnMappingConfig multi = new ExportConfig.MultiFileColumnMappingConfig(
                new ExportConfig.ColumnMappingConfig(purchaseCols),
                new ExportConfig.ColumnMappingConfig(incomeCols),
                new ExportConfig.ColumnMappingConfig(investCols),
                new ExportConfig.ColumnMappingConfig(lendCols)
        );

        Map<TxnCategory, String> categoryMapping = new EnumMap<>(TxnCategory.class);
        categoryMapping.put(TxnCategory.FOOD, "Food");
        categoryMapping.put(TxnCategory.FUN_AND_PLAY, "Fun & Chill");
        categoryMapping.put(TxnCategory.SUBSCRIPTION, "Subscription");

        ExportConfig.Profile defaultProfile = new ExportConfig.Profile(
                "yyyy-MM-dd",
                multi,
                null,
                categoryMapping
        );

        // 2. Setup single-tracker profile ('default2')
        LinkedHashMap<String, String> singleCols = new LinkedHashMap<>();
        singleCols.put("Title", "title");
        singleCols.put("Amount", "amount");
        singleCols.put("Date", "date");
        singleCols.put("Tags", "category");
        singleCols.put("Txn Id", "id");

        ExportConfig.Profile default2Profile = new ExportConfig.Profile(
                "yyyy-MM-dd",
                null,
                new ExportConfig.ColumnMappingConfig(singleCols),
                categoryMapping
        );

        Map<String, ExportConfig.Profile> profiles = new HashMap<>();
        profiles.put("default", defaultProfile);
        profiles.put("default2", default2Profile);

        exportConfig = new ExportConfig(tempDir.toString(), profiles);
        exportService = new CsvExportService(exportConfig, statementRepository, transactionRepository);

        statementId = UUID.randomUUID();
        statementRepository.saveStatement(Statement.builder().id(statementId).build());
    }

    @Test
    void shouldExportMultiTrackerFilesWithCorrectColumnsAndBucketing() throws IOException {
        // Given: 2 purchases, 1 salary (income), 1 investment, 1 pending (ignored), 1 self-transfer (ignored)
        NormalizedTransaction p1 = createTxn(statementId, TxnType.PURCHASE, TxnCategory.FOOD, 450.0,
                TxnDirection.DEBIT, LocalDateTime.of(2026, 10, 5, 12, 30),
                "Swiggy", "SWIGGY-123", "Lunch with team", "Office meal", TxnReviewStatus.MANUAL_REVIEWED);

        NormalizedTransaction p2 = createTxn(statementId, TxnType.PURCHASE, TxnCategory.FUN_AND_PLAY, 280.0,
                TxnDirection.DEBIT, LocalDateTime.of(2026, 10, 6, 18, 0),
                "Play Arena", "PLAY-99", null, "Badminton juice", TxnReviewStatus.AUTO_REVIEWED);

        NormalizedTransaction inc = createTxn(statementId, TxnType.SALARY, TxnCategory.INCOME, 85000.0,
                TxnDirection.CREDIT, LocalDateTime.of(2026, 10, 1, 10, 0),
                "Goldman Sachs", "GS-001", "Oct Salary", null, TxnReviewStatus.MANUAL_REVIEWED);

        NormalizedTransaction inv = createTxn(statementId, TxnType.INVESTMENT, null, 5000.0,
                TxnDirection.DEBIT, LocalDateTime.of(2026, 10, 10, 9, 15),
                "Zerodha Nifty 250", "ZERODHA", "SIP Investment", null, TxnReviewStatus.MANUAL_REVIEWED);

        NormalizedTransaction lend = createTxn(statementId, TxnType.LEND, null, 1500.0,
                TxnDirection.DEBIT, LocalDateTime.of(2026, 10, 8, 14, 0),
                "Rohit", "ROHIT-UPI", "Lent for dinner", null, TxnReviewStatus.MANUAL_REVIEWED);

        NormalizedTransaction borrow = createTxn(statementId, TxnType.BORROW, null, 2000.0,
                TxnDirection.CREDIT, LocalDateTime.of(2026, 10, 9, 16, 0),
                "Aman", "AMAN-UPI", null, null, TxnReviewStatus.MANUAL_REVIEWED);

        // Pending transaction - should NOT be exported
        NormalizedTransaction pending = createTxn(statementId, TxnType.PURCHASE, TxnCategory.MISCELLANEOUS, 100.0,
                TxnDirection.DEBIT, LocalDateTime.of(2026, 10, 12, 14, 0),
                "Unknown Store", "UNK-1", null, null, TxnReviewStatus.PENDING);

        // Self account transfer - should NOT be included in any multi bucket
        NormalizedTransaction transfer = createTxn(statementId, TxnType.SELF_ACCOUNT_TRANSFER, null, 10000.0,
                TxnDirection.DEBIT, LocalDateTime.of(2026, 10, 2, 8, 0),
                "Self", "ACC-2", null, null, TxnReviewStatus.MANUAL_REVIEWED);

        transactionRepository.saveTransactions(List.of(p1, p2, inc, inv, lend, borrow, pending, transfer));

        // When
        CsvExportInput input = CsvExportInput.builder().statementId(statementId).build();
        exportService.export(input, "default", tempDir.toString());

        // Then: 4 files should be created (PURCHASE, INCOME, INVEST, LEND_OR_BORROWED)
        List<Path> createdFiles;
        try (Stream<Path> stream = Files.list(tempDir)) {
            createdFiles = stream.toList();
        }

        assertThat(createdFiles).hasSize(4);

        Path purchaseFile = createdFiles.stream().filter(p -> p.getFileName().toString().startsWith("PURCHASE_")).findFirst().orElseThrow();
        Path incomeFile = createdFiles.stream().filter(p -> p.getFileName().toString().startsWith("INCOME_")).findFirst().orElseThrow();
        Path investFile = createdFiles.stream().filter(p -> p.getFileName().toString().startsWith("INVEST_")).findFirst().orElseThrow();
        Path lendFile = createdFiles.stream().filter(p -> p.getFileName().toString().startsWith("LEND_OR_BORROWED_")).findFirst().orElseThrow();

        // Verify PURCHASE CSV
        assertThat(purchaseFile.getFileName().toString()).endsWith(".csv");
        List<String> purchaseLines = Files.readAllLines(purchaseFile);
        assertThat(purchaseLines.get(0)).isEqualTo("Title,Amount,Date,Tags,Txn Id");
        // Title fallback: reviewRemarks > userComment > counterparty
        assertThat(purchaseLines).anySatisfy(line -> {
            assertThat(line).contains("Office meal"); // reviewRemark
            assertThat(line).contains("450.0");
            assertThat(line).contains("2026-10-05");
            assertThat(line).contains("Food"); // mapped tag
            assertThat(line).contains(p1.getId().toString());
        });
        assertThat(purchaseLines).anySatisfy(line -> {
            assertThat(line).contains("Badminton juice"); // userComment fallback
            assertThat(line).contains("280.0");
            assertThat(line).contains("2026-10-06");
            assertThat(line).contains("Fun & Chill"); // mapped tag
            assertThat(line).contains(p2.getId().toString());
        });
        assertThat(purchaseLines).hasSize(3); // Header + 2 rows

        // Verify INCOME CSV
        List<String> incomeLines = Files.readAllLines(incomeFile);
        assertThat(incomeLines.get(0)).isEqualTo("Title,Date,Amount,Source,Txn Id");
        assertThat(incomeLines.get(1)).contains("Oct Salary");
        assertThat(incomeLines.get(1)).contains("Goldman Sachs");
        assertThat(incomeLines.get(1)).contains("85000.0");
        assertThat(incomeLines).hasSize(2); // Header + 1 row

        // Verify INVEST CSV
        List<String> investLines = Files.readAllLines(investFile);
        assertThat(investLines.get(0)).isEqualTo("Title,Amount,Tags,Last investment on");
        assertThat(investLines.get(1)).contains("SIP Investment");
        assertThat(investLines.get(1)).contains("Zerodha Nifty 250");
        assertThat(investLines.get(1)).contains("5000.0");
        assertThat(investLines.get(1)).contains("2026-10-10");
        assertThat(investLines).hasSize(2); // Header + 1 row

        // Verify LEND_OR_BORROWED CSV
        List<String> lendLines = Files.readAllLines(lendFile);
        assertThat(lendLines.get(0)).isEqualTo("Title,Amount,Date,Source,Type,Txn Id");
        assertThat(lendLines).anySatisfy(line -> {
            assertThat(line).contains("Lent for dinner");
            assertThat(line).contains("1500.0");
            assertThat(line).contains("Rohit");
            assertThat(line).contains("LEND");
            assertThat(line).contains(lend.getId().toString());
        });
        assertThat(lendLines).anySatisfy(line -> {
            assertThat(line).contains("Aman");
            assertThat(line).contains("2000.0");
            assertThat(line).contains("BORROW");
            assertThat(line).contains(borrow.getId().toString());
        });
        assertThat(lendLines).hasSize(3); // Header + 2 rows
    }

    @Test
    void shouldFilterTransactionsByDateRangeAcrossStatements() throws IOException {
        // Given: 2 statements with transactions on different dates
        UUID stmt2 = UUID.randomUUID();
        statementRepository.saveStatement(Statement.builder().id(stmt2).build());

        NormalizedTransaction tInOct = createTxn(statementId, TxnType.PURCHASE, TxnCategory.FOOD, 150.0,
                TxnDirection.DEBIT, LocalDateTime.of(2026, 10, 15, 12, 0),
                "Cafe", "C-1", null, null, TxnReviewStatus.MANUAL_REVIEWED);

        NormalizedTransaction tInNov = createTxn(stmt2, TxnType.PURCHASE, TxnCategory.FOOD, 200.0,
                TxnDirection.DEBIT, LocalDateTime.of(2026, 11, 2, 12, 0),
                "Cafe", "C-1", null, null, TxnReviewStatus.MANUAL_REVIEWED);

        transactionRepository.saveTransactions(List.of(tInOct, tInNov));

        // When exporting with date range for Oct 1 to Oct 31
        CsvExportInput input = CsvExportInput.builder()
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .build();

        exportService.export(input, "default", tempDir.toString());

        // Then: only Oct transaction should be present
        List<Path> files;
        try (Stream<Path> stream = Files.list(tempDir)) {
            files = stream.toList();
        }
        assertThat(files).hasSize(1);
        List<String> lines = Files.readAllLines(files.get(0));
        assertThat(lines).hasSize(2); // Header + 1 row
        assertThat(lines.get(1)).contains("150.0");
        assertThat(lines.get(1)).doesNotContain("200.0");
    }

    @Test
    void shouldExportSingleConsolidatedTrackerWithSignedAmounts() throws IOException {
        // Given: 1 purchase (debit) and 1 salary (credit)
        NormalizedTransaction debitTxn = createTxn(statementId, TxnType.PURCHASE, TxnCategory.FOOD, 300.0,
                TxnDirection.DEBIT, LocalDateTime.of(2026, 10, 5, 12, 0),
                "Restaurant", "R-1", null, null, TxnReviewStatus.MANUAL_REVIEWED);

        NormalizedTransaction creditTxn = createTxn(statementId, TxnType.SALARY, TxnCategory.INCOME, 50000.0,
                TxnDirection.CREDIT, LocalDateTime.of(2026, 10, 1, 9, 0),
                "Employer", "E-1", null, null, TxnReviewStatus.MANUAL_REVIEWED);

        transactionRepository.saveTransactions(List.of(debitTxn, creditTxn));

        // When exporting using single-tracker profile 'default2'
        CsvExportInput input = CsvExportInput.builder().statementId(statementId).build();
        exportService.export(input, "default2", tempDir.toString());

        // Then
        List<Path> files;
        try (Stream<Path> stream = Files.list(tempDir)) {
            files = stream.toList();
        }
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).startsWith("CONSOLIDATED_");

        List<String> lines = Files.readAllLines(files.get(0));
        assertThat(lines.get(0)).isEqualTo("Title,Amount,Date,Tags,Txn Id");
        // Purchase (debit) should be negative amount (-300.0)
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("-300.0"));
        // Salary (credit) should be positive amount (50000.0)
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("50000.0"));
    }

    @Test
    void shouldFallbackToDefaultProfileWhenProfileIsNullOrBlank() throws IOException {
        NormalizedTransaction p = createTxn(statementId, TxnType.PURCHASE, TxnCategory.FOOD, 100.0,
                TxnDirection.DEBIT, LocalDateTime.of(2026, 10, 5, 12, 0),
                "Store", "S-1", null, null, TxnReviewStatus.MANUAL_REVIEWED);
        transactionRepository.saveTransaction(p);

        // When exporting with profile = null and profile = ""
        CsvExportInput input = CsvExportInput.builder().statementId(statementId).build();
        exportService.export(input, null, tempDir.toString());

        // Then: uses "default" profile
        List<Path> files;
        try (Stream<Path> stream = Files.list(tempDir)) {
            files = stream.toList();
        }
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).startsWith("PURCHASE_");
    }

    @Test
    void shouldThrowExceptionWhenInputIsInvalid() {
        // Neither statementId nor date range
        CsvExportInput emptyInput = CsvExportInput.builder().build();
        assertThatThrownBy(() -> exportService.export(emptyInput, "default", tempDir.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either statement id");

        // Invalid profile
        CsvExportInput validStmt = CsvExportInput.builder().statementId(statementId).build();
        assertThatThrownBy(() -> exportService.export(validStmt, "non-existent-profile", tempDir.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Export profile not found");
    }

    private NormalizedTransaction createTxn(UUID stmtId, TxnType type, TxnCategory category, double amount,
                                           TxnDirection dir, LocalDateTime datetime, String counterparty,
                                           String counterpartyId, String userComment, String reviewRemarks,
                                           TxnReviewStatus reviewStatus) {
        return NormalizedTransaction.builder()
                .id(UUID.randomUUID())
                .statementId(stmtId)
                .type(type)
                .category(category)
                .amount(amount)
                .direction(dir)
                .datetime(datetime)
                .counterparty(counterparty)
                .counterpartyId(counterpartyId)
                .userComment(userComment)
                .reviewRemarks(reviewRemarks)
                .reviewStatus(reviewStatus)
                .mode(TxnMode.UPI)
                .build();
    }
}
