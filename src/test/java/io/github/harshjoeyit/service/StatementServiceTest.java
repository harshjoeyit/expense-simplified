package io.github.harshjoeyit.service;

import io.github.harshjoeyit.core.dedup.DeduplicationResult;
import io.github.harshjoeyit.core.dedup.TransactionDeduplicationEngine;
import io.github.harshjoeyit.core.mapping.TransactionMapper;
import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.StatementReviewStatus;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.hdfc.delimited.HdfcAccountStatementParser;
import io.github.harshjoeyit.core.parse.hdfc.delimited.HdfcCreditCardStatementParser;
import io.github.harshjoeyit.core.parse.scapia.delimited.ScapiaCreditCardStatementParser;
import io.github.harshjoeyit.core.repository.StatementRepository;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import io.github.harshjoeyit.model.StatementImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private HdfcAccountStatementParser hdfcAccountParser;
    @Mock
    private HdfcCreditCardStatementParser hdfcCreditCardParser;
    @Mock
    private ScapiaCreditCardStatementParser scapiaCreditCardParser;
    @Mock
    private TransactionMapper mapper;
    @Mock
    private StatementRepository statementRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionDeduplicationEngine deduplicationEngine;

    private StatementService statementService;

    @BeforeEach
    void setUp() {
        statementService = new StatementService(
                hdfcAccountParser,
                hdfcCreditCardParser,
                scapiaCreditCardParser,
                mapper,
                statementRepository,
                transactionRepository,
                deduplicationEngine
        );
    }

    @Test
    void getStatementSummaries_returnsSummariesWithTransactionCounts_respectingLimit() {
        UUID stmt1Id = UUID.randomUUID();
        UUID stmt2Id = UUID.randomUUID();
        UUID stmt3Id = UUID.randomUUID();
        UUID stmt4Id = UUID.randomUUID();

        Statement stmt1 = Statement.builder().id(stmt1Id).instrumentId("ACC1").createdAt(LocalDateTime.now()).build();
        Statement stmt2 = Statement.builder().id(stmt2Id).instrumentId("ACC2").createdAt(LocalDateTime.now().minusHours(1)).build();
        Statement stmt3 = Statement.builder().id(stmt3Id).instrumentId("ACC3").createdAt(LocalDateTime.now().minusHours(2)).build();
        Statement stmt4 = Statement.builder().id(stmt4Id).instrumentId("ACC4").createdAt(LocalDateTime.now().minusHours(3)).build();

        when(statementRepository.findAll()).thenReturn(List.of(stmt1, stmt2, stmt3, stmt4));

        NormalizedTransaction pendingTxn = NormalizedTransaction.builder().statementId(stmt1Id).reviewStatus(TxnReviewStatus.PENDING).build();
        NormalizedTransaction reviewedTxn = NormalizedTransaction.builder().statementId(stmt1Id).reviewStatus(TxnReviewStatus.MANUAL_REVIEWED).build();

        when(transactionRepository.findByStatementId(stmt1Id)).thenReturn(List.of(pendingTxn, reviewedTxn));
        when(transactionRepository.findPendingReviewTransactionsByStatementId(stmt1Id)).thenReturn(List.of(pendingTxn));

        when(transactionRepository.findByStatementId(stmt2Id)).thenReturn(List.of());
        when(transactionRepository.findPendingReviewTransactionsByStatementId(stmt2Id)).thenReturn(List.of());

        // Limit = 2
        List<StatementService.StatementSummary> summaries = statementService.getStatementSummaries(2);

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).statement()).isEqualTo(stmt1);
        assertThat(summaries.get(0).totalTransactions()).isEqualTo(2);
        assertThat(summaries.get(0).pendingTransactions()).isEqualTo(1);

        assertThat(summaries.get(1).statement()).isEqualTo(stmt2);
        assertThat(summaries.get(1).totalTransactions()).isZero();
        assertThat(summaries.get(1).pendingTransactions()).isZero();
    }

    @Test
    void getStatementSummaries_defaultLimitIsThree_whenLimitIsZeroOrNegative() {
        Statement stmt1 = Statement.builder().id(UUID.randomUUID()).createdAt(LocalDateTime.now()).build();
        Statement stmt2 = Statement.builder().id(UUID.randomUUID()).createdAt(LocalDateTime.now()).build();
        Statement stmt3 = Statement.builder().id(UUID.randomUUID()).createdAt(LocalDateTime.now()).build();
        Statement stmt4 = Statement.builder().id(UUID.randomUUID()).createdAt(LocalDateTime.now()).build();

        when(statementRepository.findAll()).thenReturn(List.of(stmt1, stmt2, stmt3, stmt4));
        when(transactionRepository.findByStatementId(org.mockito.ArgumentMatchers.any())).thenReturn(Collections.emptyList());
        when(transactionRepository.findPendingReviewTransactionsByStatementId(org.mockito.ArgumentMatchers.any())).thenReturn(Collections.emptyList());

        List<StatementService.StatementSummary> summaries = statementService.getStatementSummaries(0);
        assertThat(summaries).hasSize(3);
    }

    @Test
    void importStatement_crossInstrumentCollision_doesNotCreateStatementOrSaveTransactions(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        java.io.File tempFile = java.nio.file.Files.createFile(tempDir.resolve("test.csv")).toFile();
        NormalizedTransaction parsed = NormalizedTransaction.builder().datetime(LocalDateTime.now()).amount(100.0).build();
        when(hdfcAccountParser.parseDelimited(any())).thenReturn(List.of(parsed));

        DeduplicationResult collisionResult = new DeduplicationResult(
                List.of(),
                List.of(),
                List.of(),
                "HDFC_SALARY"
        );
        when(deduplicationEngine.deduplicate("HDFC_SAVINGS", List.of(parsed))).thenReturn(collisionResult);

        StatementImportResult result = statementService.importStatement(
                tempFile, FileType.DELIMITED, io.github.harshjoeyit.cli.command.StatementType.HDFC_ACC, "HDFC_SAVINGS");

        assertThat(result.statement()).isNull();
        assertThat(result.dedupResult().hasCrossInstrumentCollision()).isTrue();
        assertThat(result.dedupResult().crossInstrumentCollision()).isEqualTo("HDFC_SALARY");
        verifyNoInteractions(statementRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void importStatement_fullDuplicate_doesNotCreateStatementOrSaveTransactions(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        java.io.File tempFile = java.nio.file.Files.createFile(tempDir.resolve("test.csv")).toFile();
        NormalizedTransaction parsed = NormalizedTransaction.builder().datetime(LocalDateTime.now()).amount(100.0).build();
        when(hdfcAccountParser.parseDelimited(any())).thenReturn(List.of(parsed));

        DeduplicationResult fullDupResult = new DeduplicationResult(
                List.of(),
                List.of(new io.github.harshjoeyit.core.dedup.DuplicateMatch(parsed, parsed)),
                List.of(),
                null
        );
        when(deduplicationEngine.deduplicate("HDFC_SAVINGS", List.of(parsed))).thenReturn(fullDupResult);

        StatementImportResult result = statementService.importStatement(
                tempFile, FileType.DELIMITED, io.github.harshjoeyit.cli.command.StatementType.HDFC_ACC, "HDFC_SAVINGS");

        assertThat(result.statement()).isNull();
        assertThat(result.dedupResult().isFullDuplicate()).isTrue();
        verifyNoInteractions(statementRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void importStatement_withNewTransactions_createsStatementEnrichesAndSavesTransactions(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        java.io.File tempFile = java.nio.file.Files.createFile(tempDir.resolve("test.csv")).toFile();
        NormalizedTransaction parsed = NormalizedTransaction.builder().datetime(LocalDateTime.now()).amount(100.0).build();
        when(hdfcAccountParser.parseDelimited(any())).thenReturn(List.of(parsed));

        DeduplicationResult newTxnResult = new DeduplicationResult(
                List.of(parsed),
                List.of(),
                List.of(),
                null
        );
        when(deduplicationEngine.deduplicate("HDFC_SAVINGS", List.of(parsed))).thenReturn(newTxnResult);
        when(mapper.enrich(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StatementImportResult result = statementService.importStatement(
                tempFile, FileType.DELIMITED, io.github.harshjoeyit.cli.command.StatementType.HDFC_ACC, "HDFC_SAVINGS");

        assertThat(result.statement()).isNotNull();
        assertThat(result.statement().instrumentId()).isEqualTo("HDFC_SAVINGS");
        assertThat(parsed.getInstrumentId()).isEqualTo("HDFC_SAVINGS");
        assertThat(parsed.getStatementId()).isEqualTo(result.statement().id());

        verify(statementRepository).saveStatement(any());
        verify(transactionRepository).saveTransactions(List.of(parsed));
    }
}
