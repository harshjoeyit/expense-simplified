package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.StatementReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryStatementRepositoryTest {

    private InMemoryStatementRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryStatementRepository();
    }

    @Test
    void saveAndFindById_returnsSavedStatement() {
        UUID statementId = UUID.randomUUID();
        Statement statement = Statement.builder()
                .id(statementId)
                .fileType(FileType.DELIMITED)
                .filePath("/path/to/statement.csv")
                .instrumentId("HDFC_ACC")
                .createdAt(LocalDateTime.now())
                .reviewStatus(StatementReviewStatus.PENDING_REVIEW)
                .build();

        repository.saveStatement(statement);

        Optional<Statement> found = repository.findById(statementId);
        assertThat(found).isPresent().contains(statement);
    }

    @Test
    void findAll_returnsStatementsSortedNewestFirst() {
        LocalDateTime now = LocalDateTime.now();

        Statement older = Statement.builder()
                .id(UUID.randomUUID())
                .instrumentId("OLDER")
                .createdAt(now.minusHours(2))
                .build();

        Statement newer = Statement.builder()
                .id(UUID.randomUUID())
                .instrumentId("NEWER")
                .createdAt(now)
                .build();

        Statement oldest = Statement.builder()
                .id(UUID.randomUUID())
                .instrumentId("OLDEST")
                .createdAt(now.minusDays(1))
                .build();

        repository.saveStatement(older);
        repository.saveStatement(newer);
        repository.saveStatement(oldest);

        List<Statement> all = repository.findAll();

        assertThat(all).containsExactly(newer, older, oldest);
    }

    @Test
    void findLatest_returnsMostRecentStatement() {
        LocalDateTime now = LocalDateTime.now();

        Statement older = Statement.builder()
                .id(UUID.randomUUID())
                .createdAt(now.minusHours(1))
                .build();

        Statement newer = Statement.builder()
                .id(UUID.randomUUID())
                .createdAt(now)
                .build();

        repository.saveStatement(older);
        repository.saveStatement(newer);

        Optional<Statement> latest = repository.findLatest();
        assertThat(latest).isPresent().contains(newer);
    }
}
