package io.github.harshjoeyit.service;

import io.github.harshjoeyit.core.mapping.TransactionMapper;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.StatementReviewStatus;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.hdfc.delimited.HdfcAccountStatementParser;
import io.github.harshjoeyit.core.parse.hdfc.delimited.HdfcCreditCardStatementParser;
import io.github.harshjoeyit.core.parse.scapia.delimited.ScapiaCreditCardStatementParser;
import io.github.harshjoeyit.core.repository.StatementRepository;
import io.github.harshjoeyit.core.repository.TransactionRepository;
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
import static org.mockito.Mockito.when;

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

    private StatementService statementService;

    @BeforeEach
    void setUp() {
        statementService = new StatementService(
                hdfcAccountParser,
                hdfcCreditCardParser,
                scapiaCreditCardParser,
                mapper,
                statementRepository,
                transactionRepository
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
}
