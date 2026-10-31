package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.StatementReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.persistence=sqlite",
        "spring.shell.interactive.enabled=false",
        "spring.datasource.url=jdbc:sqlite:target/test-expense-simplified.db?journal_mode=WAL&foreign_keys=ON"
})
@TestExecutionListeners(listeners = {
        DependencyInjectionTestExecutionListener.class
})
class JdbcStatementRepositoryTest {

    @Autowired
    private JdbcStatementRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM statements").update();
    }

    @Test
    void saveAndFindById_returnsSavedStatement() {
        UUID statementId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        Statement statement = Statement.builder()
                .id(statementId)
                .fileType(FileType.DELIMITED)
                .filePath("/path/to/statement.csv")
                .instrumentId("HDFC_ACC")
                .createdAt(now)
                .updatedAt(now)
                .reviewStatus(StatementReviewStatus.PENDING_REVIEW)
                .build();

        repository.saveStatement(statement);

        Optional<Statement> found = repository.findById(statementId);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(statementId);
        assertThat(found.get().fileType()).isEqualTo(FileType.DELIMITED);
        assertThat(found.get().filePath()).isEqualTo("/path/to/statement.csv");
        assertThat(found.get().instrumentId()).isEqualTo("HDFC_ACC");
        assertThat(found.get().reviewStatus()).isEqualTo(StatementReviewStatus.PENDING_REVIEW);
    }

    @Test
    void saveStatement_updatesExistingStatementOnConflict() {
        UUID statementId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        Statement statement = Statement.builder()
                .id(statementId)
                .fileType(FileType.DELIMITED)
                .filePath("/path/to/statement.csv")
                .instrumentId("HDFC_ACC")
                .createdAt(now)
                .updatedAt(now)
                .reviewStatus(StatementReviewStatus.PENDING_REVIEW)
                .build();

        repository.saveStatement(statement);

        Statement completed = statement.withMarkComplete();
        repository.saveStatement(completed);

        Optional<Statement> found = repository.findById(statementId);
        assertThat(found).isPresent();
        assertThat(found.get().reviewStatus()).isEqualTo(StatementReviewStatus.COMPLETED);
    }

    @Test
    void findAll_returnsStatementsSortedNewestFirst() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        Statement older = Statement.builder()
                .id(UUID.randomUUID())
                .fileType(FileType.DELIMITED)
                .filePath("/path/older.csv")
                .instrumentId("OLDER")
                .createdAt(now.minusHours(2))
                .updatedAt(now.minusHours(2))
                .build();

        Statement newer = Statement.builder()
                .id(UUID.randomUUID())
                .fileType(FileType.DELIMITED)
                .filePath("/path/newer.csv")
                .instrumentId("NEWER")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Statement oldest = Statement.builder()
                .id(UUID.randomUUID())
                .fileType(FileType.DELIMITED)
                .filePath("/path/oldest.csv")
                .instrumentId("OLDEST")
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build();

        repository.saveStatement(older);
        repository.saveStatement(newer);
        repository.saveStatement(oldest);

        List<Statement> all = repository.findAll();

        assertThat(all).hasSize(3);
        assertThat(all.get(0).id()).isEqualTo(newer.id());
        assertThat(all.get(1).id()).isEqualTo(older.id());
        assertThat(all.get(2).id()).isEqualTo(oldest.id());
    }

    @Test
    void findLatest_returnsMostRecentStatement() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        Statement older = Statement.builder()
                .id(UUID.randomUUID())
                .fileType(FileType.DELIMITED)
                .filePath("/path/older.csv")
                .instrumentId("OLDER")
                .createdAt(now.minusHours(1))
                .updatedAt(now.minusHours(1))
                .build();

        Statement newer = Statement.builder()
                .id(UUID.randomUUID())
                .fileType(FileType.DELIMITED)
                .filePath("/path/newer.csv")
                .instrumentId("NEWER")
                .createdAt(now)
                .updatedAt(now)
                .build();

        repository.saveStatement(older);
        repository.saveStatement(newer);

        Optional<Statement> latest = repository.findLatest();
        assertThat(latest).isPresent();
        assertThat(latest.get().id()).isEqualTo(newer.id());
    }
}
