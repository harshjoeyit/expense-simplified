package io.github.harshjoeyit.cli.command;

import io.github.harshjoeyit.core.dedup.DeduplicationResult;
import io.github.harshjoeyit.core.dedup.DuplicateMatch;
import io.github.harshjoeyit.core.dedup.StatementOverlapSummary;
import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnType;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    private StatementCommands commands;

    @BeforeEach
    void setUp() {
        commands = new StatementCommands(
                statementService,
                instrumentService,
                reviewService,
                csvExportService,
                splitService,
                friendService
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
}
