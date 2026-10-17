package io.github.harshjoeyit.service;

import io.github.harshjoeyit.cli.command.StatementType;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final TransactionMapper mapper;

    public StatementService(
            HdfcAccountStatementParser hdfcAccountParser,
            HdfcCreditCardStatementParser hdfcCreditCardParser,
            ScapiaCreditCardStatementParser scapiaCreditCardParser,
            TransactionMapper mapper,
            StatementRepository statementRepository,
            TransactionRepository transactionRepository) {
        this.hdfcAccountParser = hdfcAccountParser;
        this.hdfcCreditCardParser = hdfcCreditCardParser;
        this.scapiaCreditCardParser = scapiaCreditCardParser;
        this.mapper = mapper;
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
    }

    public Statement createStatement(FileType fileType, String filepath, String instrumentId) {
        Statement statement = Statement.builder()
                .fileType(fileType)
                .filePath(filepath)
                .instrumentId(instrumentId)
                .uploadTime(LocalDateTime.now())
                .build();

        statementRepository.saveStatement(statement);

        return statement;
    }

    public List<NormalizedTransaction> parseStatement(File file, FileType fileType,
                                                      StatementType type, String instrumentId, UUID statementId) throws IOException {
        Parser parser = switch (type) {
            case HDFC_ACC -> hdfcAccountParser;
            case HDFC_CC -> hdfcCreditCardParser;
            case SCAPIA_CC -> scapiaCreditCardParser;
        };

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<NormalizedTransaction> transactions =  new ArrayList<>();
            if (fileType == FileType.DELIMITED) {
                transactions = parser.parseDelimited(reader, instrumentId, statementId);
            }
            List<NormalizedTransaction> enrichedTransactions = mapper.enrich(transactions);
            transactionRepository.saveTransactions(enrichedTransactions);
            return enrichedTransactions;
        }
    }
}
