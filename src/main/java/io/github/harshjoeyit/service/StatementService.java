package io.github.harshjoeyit.service;

import io.github.harshjoeyit.cli.command.StatementType;
import io.github.harshjoeyit.core.dedup.DeduplicationResult;
import io.github.harshjoeyit.core.dedup.TransactionDeduplicationEngine;
import io.github.harshjoeyit.core.mapping.TransactionMapper;
import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.parse.Parser;
import io.github.harshjoeyit.core.parse.hdfc.delimited.HdfcAccountStatementParser;
import io.github.harshjoeyit.core.parse.hdfc.delimited.HdfcCreditCardStatementParser;
import io.github.harshjoeyit.core.parse.scapia.delimited.ScapiaCreditCardStatementParser;
import io.github.harshjoeyit.core.repository.StatementRepository;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import io.github.harshjoeyit.model.StatementImportResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class StatementService {

    private final HdfcAccountStatementParser hdfcAccountParser;
    private final HdfcCreditCardStatementParser hdfcCreditCardParser;
    private final ScapiaCreditCardStatementParser scapiaCreditCardParser;
    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionDeduplicationEngine deduplicationEngine;
    private final TransactionMapper mapper;

    public StatementService(
            HdfcAccountStatementParser hdfcAccountParser,
            HdfcCreditCardStatementParser hdfcCreditCardParser,
            ScapiaCreditCardStatementParser scapiaCreditCardParser,
            TransactionMapper mapper,
            StatementRepository statementRepository,
            TransactionRepository transactionRepository,
            TransactionDeduplicationEngine deduplicationEngine) {
        this.hdfcAccountParser = hdfcAccountParser;
        this.hdfcCreditCardParser = hdfcCreditCardParser;
        this.scapiaCreditCardParser = scapiaCreditCardParser;
        this.mapper = mapper;
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.deduplicationEngine = deduplicationEngine;
    }

    public StatementImportResult importStatement(
            File file, FileType fileType, StatementType type, String instrumentId
    ) throws IOException {

        // Parse
        List<NormalizedTransaction> incomingTxns = parseStatement(file, fileType, type);

        // Check for duplicates
        DeduplicationResult dedupResult = deduplicationEngine.deduplicate(instrumentId, incomingTxns);

        Statement newStatement = null;

        if (!dedupResult.hasCrossInstrumentCollision()
                && !dedupResult.isFullDuplicate()
                && !dedupResult.newTransactions().isEmpty()
        ) {
            // Create new statement
            newStatement = createStatement(fileType, file, instrumentId);
            UUID statementId = newStatement.id();

            // Link new transactions to this statement and instrument
            List<NormalizedTransaction> newTxns = dedupResult.newTransactions();
            newTxns.forEach(txn -> {
                txn.setInstrumentId(instrumentId);
                txn.setStatementId(statementId);
            });

            // Enrich and persist new transactions
            List<NormalizedTransaction> enrichedTxns = mapper.enrich(newTxns);
            transactionRepository.saveTransactions(enrichedTxns);
        }

        return new StatementImportResult(newStatement, dedupResult);
    }

    private List<NormalizedTransaction> parseStatement(
            File file, FileType fileType, StatementType type
    ) throws IOException  {

        Parser parser = switch (type) {
            case HDFC_ACC -> hdfcAccountParser;
            case HDFC_CC -> hdfcCreditCardParser;
            case SCAPIA_CC -> scapiaCreditCardParser;
        };

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            if (fileType == FileType.DELIMITED) {
                return parser.parseDelimited(reader);
            } else {
                throw new UnsupportedOperationException("Not implemented fileType: " + fileType);
            }
        }
    }

    private Statement createStatement(FileType fileType, File file, String instrumentId) {
        if (fileType == null || file == null || instrumentId == null || instrumentId.isBlank()) {
            return null;
        }

        Statement statement = Statement.builder()
                .fileType(fileType)
                .filePath(file.getAbsolutePath())
                .instrumentId(instrumentId)
                .createdAt(LocalDateTime.now())
                .build();

        statementRepository.saveStatement(statement);

        return statement;
    }

    public record StatementSummary(
            Statement statement,
            int totalTransactions,
            int pendingTransactions
    ) {}

    public List<StatementSummary> getStatementSummaries(int limit) {
        int max = limit > 0 ? limit : 3;
        return statementRepository.findAll()
                .stream()
                .limit(max)
                .map(statement -> {
                    int total = transactionRepository.findByStatementId(statement.id()).size();
                    int pending = transactionRepository.findPendingReviewTransactionsByStatementId(statement.id()).size();
                    return new StatementSummary(statement, total, pending);
                })
                .toList();
    }
}
