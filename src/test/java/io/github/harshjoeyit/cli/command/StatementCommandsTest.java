package io.github.harshjoeyit.cli.command;

import io.github.harshjoeyit.core.dedup.DeduplicationResult;
import io.github.harshjoeyit.core.dedup.DuplicateMatch;
import io.github.harshjoeyit.core.dedup.StatementOverlapSummary;
import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
import io.github.harshjoeyit.export.model.CsvExportResult;
import io.github.harshjoeyit.export.model.ExportExclusionReason;
import io.github.harshjoeyit.export.model.ExportPreflightResult;
import io.github.harshjoeyit.export.service.CsvExportService;
import io.github.harshjoeyit.model.StatementImportResult;
import io.github.harshjoeyit.service.InstrumentService;
import io.github.harshjoeyit.service.ReviewService;
import io.github.harshjoeyit.service.StatementService;
import io.github.harshjoeyit.split.service.FriendService;
import io.github.harshjoeyit.split.service.SplitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementCommandsTest {

    @Mock private StatementService statementService;
    @Mock private InstrumentService instrumentService;
    @Mock private ReviewService reviewService;
    @Mock private CsvExportService csvExportService;
    @Mock private SplitService splitService;
    @Mock private FriendService friendService;
    @Mock private io.github.harshjoeyit.enrichment.service.EnrichmentService enrichmentService;

    private StatementCommands commands;

    @BeforeEach
    void setUp() {
        commands = new StatementCommands(
                statementService,
                instrumentService,
                reviewService,
                csvExportService,
                splitService,
                friendService,
                enrichmentService
        );
    }

    @Test
    @DisplayName("Cross-instrument collision displays warning banner and aborts")
    void parse_whenCrossInstrumentCollision_returnsWarningBanner(@TempDir Path tempDir) throws Exception {
        File file = Files.createFile(tempDir.resolve("test.csv")).toFile();
        when(instrumentService.getStatementType("HDFC_SAVINGS")).thenReturn(Optional.of(StatementType.HDFC_ACC));

        DeduplicationResult collisionResult = new DeduplicationResult(
                List.of(),
                List.of(),
                List.of(),
                "HDFC_SALARY"
        );
        when(statementService.importStatement(eq(file), eq(FileType.DELIMITED), eq(StatementType.HDFC_ACC), eq("HDFC_SAVINGS")))
                .thenReturn(new StatementImportResult(null, collisionResult));

        String output = commands.parse(file, "HDFC_SAVINGS");

        assertThat(output)
                .contains("Cross-Instrument Collision Detected!")
                .contains("HDFC_SALARY")
                .contains("Statement was not saved. Please verify the instrument.");
    }

    @Test
    @DisplayName("100% duplicate statement displays overlap table and does not save")
    void parse_when100PercentDuplicate_returnsDuplicateWarningAndOverlapTable(@TempDir Path tempDir) throws Exception {
        File file = Files.createFile(tempDir.resolve("test.csv")).toFile();
        when(instrumentService.getStatementType("HDFC_ACC")).thenReturn(Optional.of(StatementType.HDFC_ACC));

        UUID existingStmtId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder().build();
        StatementOverlapSummary summary = new StatementOverlapSummary(
                existingStmtId,
                "HDFC_ACC",
                25,
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2024, 8, 31)
        );

        DeduplicationResult fullDupResult = new DeduplicationResult(
                List.of(),
                List.of(new DuplicateMatch(txn, txn)),
                List.of(summary),
                null
        );
        when(statementService.importStatement(eq(file), eq(FileType.DELIMITED), eq(StatementType.HDFC_ACC), eq("HDFC_ACC")))
                .thenReturn(new StatementImportResult(null, fullDupResult));

        String output = commands.parse(file, "HDFC_ACC");

        assertThat(output)
                .contains("Duplicate Statement: All 1 transactions have already been imported!")
                .contains(existingStmtId.toString())
                .contains("2024-08-01 to 2024-08-31")
                .contains("Statement was not saved. No new transactions to import.");
    }

    @Test
    @DisplayName("Partial duplicates displays skipped table and table of new transactions")
    void parse_whenPartialDuplicate_returnsSkippedWarningAndTable(@TempDir Path tempDir) throws Exception {
        File file = Files.createFile(tempDir.resolve("test.csv")).toFile();
        when(instrumentService.getStatementType("HDFC_ACC")).thenReturn(Optional.of(StatementType.HDFC_ACC));

        UUID existingStmtId = UUID.randomUUID();
        UUID newStmtId = UUID.randomUUID();
        Statement statement = Statement.builder().id(newStmtId).build();

        NormalizedTransaction newTxn = NormalizedTransaction.builder()
                .statementId(newStmtId)
                .datetime(LocalDateTime.parse("2024-09-01T10:00:00"))
                .amount(500.0)
                .direction(TxnDirection.DEBIT)
                .type(TxnType.PURCHASE)
                .counterparty("Swiggy")
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        NormalizedTransaction dupTxn = NormalizedTransaction.builder().build();
        StatementOverlapSummary summary = new StatementOverlapSummary(
                existingStmtId,
                "HDFC_ACC",
                10,
                LocalDate.of(2024, 8, 15),
                LocalDate.of(2024, 8, 25)
        );

        DeduplicationResult partialResult = new DeduplicationResult(
                List.of(newTxn),
                List.of(new DuplicateMatch(dupTxn, dupTxn)),
                List.of(summary),
                null
        );
        when(statementService.importStatement(eq(file), eq(FileType.DELIMITED), eq(StatementType.HDFC_ACC), eq("HDFC_ACC")))
                .thenReturn(new StatementImportResult(statement, partialResult));
        when(reviewService.findPendingReviewTransaction(newStmtId)).thenReturn(List.of(newTxn));

        String output = commands.parse(file, "HDFC_ACC");

        assertThat(output)
                .contains("Transactions Summary:")
                .contains("Total in file: 2")
                .contains("Duplicates skipped: 1 (already recorded in database)")
                .contains("New transactions imported: 1")
                .contains(existingStmtId.toString())
                .contains("2024-08-15 to 2024-08-25")
                .contains("StatementId: " + newStmtId)
                .contains("Imported 1 new transaction(s) (out of 2 in file)");
    }

    @Test
    @DisplayName("0% duplicates displays standard parsed table without duplicate warning")
    void parse_whenNoDuplicates_returnsSuccessTable(@TempDir Path tempDir) throws Exception {
        File file = Files.createFile(tempDir.resolve("test.csv")).toFile();
        when(instrumentService.getStatementType("HDFC_ACC")).thenReturn(Optional.of(StatementType.HDFC_ACC));

        UUID newStmtId = UUID.randomUUID();
        Statement statement = Statement.builder().id(newStmtId).build();

        NormalizedTransaction newTxn = NormalizedTransaction.builder()
                .statementId(newStmtId)
                .datetime(LocalDateTime.parse("2024-09-01T10:00:00"))
                .amount(500.0)
                .direction(TxnDirection.DEBIT)
                .type(TxnType.PURCHASE)
                .counterparty("Swiggy")
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        DeduplicationResult cleanResult = new DeduplicationResult(
                List.of(newTxn),
                List.of(),
                List.of(),
                null
        );
        when(statementService.importStatement(eq(file), eq(FileType.DELIMITED), eq(StatementType.HDFC_ACC), eq("HDFC_ACC")))
                .thenReturn(new StatementImportResult(statement, cleanResult));
        when(reviewService.findPendingReviewTransaction(newStmtId)).thenReturn(List.of(newTxn));

        String output = commands.parse(file, "HDFC_ACC");

        assertThat(output)
                .doesNotContain("Duplicate")
                .doesNotContain("Cross-Instrument")
                .contains("StatementId: " + newStmtId)
                .contains("Parsed 1 transactions");
    }

    @Test
    @DisplayName("export displays generated file names and absolute paths with summary counts")
    void export_whenFilesCreated_returnsFormattedFileNamesAndPaths() {
        UUID stmtId = UUID.randomUUID();
        Path fakeExport = Path.of("./export/PURCHASE_1793543542.csv");
        when(csvExportService.exportPreflight(any())).thenReturn(new ExportPreflightResult(1, Map.of()));
        when(csvExportService.export(any(), eq("default"), eq("./export"))).thenReturn(new CsvExportResult(1, Map.of(), List.of(fakeExport)));

        String output = commands.export(stmtId, null, null, "default", "./export", false);

        assertThat(output)
                .contains("Export completed successfully:")
                .contains("PURCHASE_1793543542.csv")
                .contains(fakeExport.toAbsolutePath().normalize().toString())
                .contains("Exported: 1 transaction(s)");
    }

    @Test
    @DisplayName("export when no files created returns informative message")
    void export_whenNoFilesCreated_returnsNoTransactionsMessage() {
        UUID stmtId = UUID.randomUUID();
        when(csvExportService.exportPreflight(any())).thenReturn(new ExportPreflightResult(0, Map.of()));
        when(csvExportService.export(any(), eq("default"), eq("./export"))).thenReturn(new CsvExportResult(0, Map.of(), List.of()));

        String output = commands.export(stmtId, null, null, "default", "./export", false);

        assertThat(output).isEqualTo("No reviewed transactions found to export.");
    }

    @Test
    @DisplayName("export with yes flag bypasses prompt and reports excluded transactions in summary")
    void export_whenPendingExcludedAndYesBypass_exportsWithoutPromptAndShowsSummary() {
        UUID stmtId = UUID.randomUUID();
        Path fakeExport = Path.of("./export/PURCHASE_1793543542.csv");
        Map<ExportExclusionReason, Integer> excluded = Map.of(ExportExclusionReason.PENDING_REVIEW, 3);
        when(csvExportService.exportPreflight(any())).thenReturn(new ExportPreflightResult(5, excluded));
        when(csvExportService.export(any(), eq("default"), eq("./export"))).thenReturn(new CsvExportResult(5, excluded, List.of(fakeExport)));

        String output = commands.export(stmtId, null, null, "default", "./export", true);

        assertThat(output)
                .contains("Export completed successfully:")
                .contains("Exported: 5 transaction(s)")
                .contains("Excluded: 3 transaction(s)")
                .contains("Pending user review: 3");
    }

    @Test
    @DisplayName("export when pending excluded and prompt not confirmed cancels export")
    void export_whenPendingExcludedAndPromptNotConfirmed_cancelsExport() {
        UUID stmtId = UUID.randomUUID();
        Map<ExportExclusionReason, Integer> excluded = Map.of(ExportExclusionReason.PENDING_REVIEW, 2);
        when(csvExportService.exportPreflight(any())).thenReturn(new ExportPreflightResult(4, excluded));

        // When yes is false and headless/null terminal defaults to "N", prompt returns N
        String output = commands.export(stmtId, null, null, "default", "./export", false);

        assertThat(output).isEqualTo("Export cancelled.");
    }

    @Test
    @DisplayName("parse triggers async enrichment when new statement is imported")
    void parse_triggersAsyncEnrichment(@TempDir Path tempDir) throws Exception {
        File file = Files.createFile(tempDir.resolve("test.csv")).toFile();
        when(instrumentService.getStatementType("HDFC_ACC")).thenReturn(Optional.of(StatementType.HDFC_ACC));

        UUID newStmtId = UUID.randomUUID();
        Statement statement = Statement.builder().id(newStmtId).build();

        NormalizedTransaction newTxn = NormalizedTransaction.builder()
                .statementId(newStmtId)
                .datetime(LocalDateTime.parse("2024-09-01T10:00:00"))
                .amount(500.0)
                .direction(TxnDirection.DEBIT)
                .type(TxnType.PURCHASE)
                .counterparty("Swiggy")
                .reviewStatus(TxnReviewStatus.PENDING)
                .build();

        DeduplicationResult cleanResult = new DeduplicationResult(
                List.of(newTxn),
                List.of(),
                List.of(),
                null
        );
        when(statementService.importStatement(eq(file), eq(FileType.DELIMITED), eq(StatementType.HDFC_ACC), eq("HDFC_ACC")))
                .thenReturn(new StatementImportResult(statement, cleanResult));
        when(reviewService.findPendingReviewTransaction(newStmtId)).thenReturn(List.of(newTxn));

        commands.parse(file, "HDFC_ACC");

        org.mockito.Mockito.verify(enrichmentService).enrichStatementAsync(newStmtId);
    }

    @Test
    @DisplayName("review displays bullet points for enriched items beneath transaction line")
    void review_displaysBulletPointsForEnrichedItems() throws Exception {
        UUID stmtId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .id(txnId)
                .statementId(stmtId)
                .amount(290.0)
                .direction(TxnDirection.DEBIT)
                .datetime(LocalDateTime.parse("2024-09-01T10:00:00"))
                .counterparty("SWIGGY")
                .mode(TxnMode.UPI)
                .type(TxnType.PURCHASE)
                .build();

        when(reviewService.findPendingReviewTransaction(stmtId)).thenReturn(List.of(txn));
        when(reviewService.getTransaction(txnId)).thenReturn(Optional.of(txn));

        io.github.harshjoeyit.enrichment.model.TransactionItem item1 = io.github.harshjoeyit.enrichment.model.TransactionItem.builder()
                .id(1L)
                .transactionId(txnId)
                .itemName("All American Burger")
                .totalPrice(210.0)
                .outletName("Truffles")
                .build();
        io.github.harshjoeyit.enrichment.model.TransactionItem item2 = io.github.harshjoeyit.enrichment.model.TransactionItem.builder()
                .id(2L)
                .transactionId(txnId)
                .itemName("Peri Peri Fries")
                .totalPrice(80.0)
                .outletName("Truffles")
                .build();
        when(enrichmentService.getItemsForTransaction(txnId)).thenReturn(List.of(item1, item2));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream("q\n".getBytes());
        org.jline.terminal.Terminal terminal = org.jline.terminal.TerminalBuilder.builder()
                .streams(in, out)
                .build();

        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<org.jline.terminal.Terminal> terminalProvider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        when(terminalProvider.getObject()).thenReturn(terminal);
        org.springframework.test.util.ReflectionTestUtils.setField(commands, "terminalProvider", terminalProvider);

        commands.review(stmtId);

        String console = out.toString();
        assertThat(console)
                .contains("SWIGGY")
                .contains("- Truffles: All American Burger | ₹210.00")
                .contains("- Truffles: Peri Peri Fries | ₹80.00")
                .contains("[c] Categorize  [s] Split");
    }
}
