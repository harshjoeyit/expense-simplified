package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.StatementReviewStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@ConditionalOnProperty(name = "app.persistence", havingValue = "sqlite", matchIfMissing = true)
public class JdbcStatementRepository implements StatementRepository {

    private final JdbcClient jdbcClient;

    // RowMapper to map SQLite TEXT columns to Java UUID, Enums, and LocalDateTime
    private static final RowMapper<Statement> ROW_MAPPER = (rs, rowNum) -> {
        // Columns in DB: id, file_type, file_path, instrument_id, review_status, created_at, updated_at
        FileType fileType = rs.getString("file_type") != null
                ? FileType.valueOf(rs.getString("file_type"))
                : null;
        StatementReviewStatus statementReviewStatus = rs.getString("review_status") != null
                ? StatementReviewStatus.valueOf(rs.getString("review_status"))
                : null;

        return Statement.builder()
                .id(UUID.fromString(rs.getString("id")))
                .fileType(fileType)
                .filePath(rs.getString("file_path"))
                .instrumentId(rs.getString("instrument_id"))
                .reviewStatus(statementReviewStatus)
                .createdAt(LocalDateTime.parse(rs.getString("created_at")))
                .updatedAt(LocalDateTime.parse(rs.getString("updated_at")))
                .build();
    };

    public JdbcStatementRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Statement> findById(UUID statementId) {
        if (statementId == null) {
            return Optional.empty();
        }
        return jdbcClient.sql("SELECT * FROM statements WHERE id = :id")
                .param("id", statementId.toString())
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public Optional<Statement> findLatest() {
        // Todo: index on created_at
        return jdbcClient.sql("SELECT * FROM statements ORDER BY created_at DESC LIMIT 1")
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public List<Statement> findAll() {
        return jdbcClient.sql("SELECT * FROM statements ORDER BY created_at DESC")
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public void saveStatement(Statement statement) {
        if (statement == null || statement.id() == null) {
            log.warn("Cannot save null statement or statement without ID");
            return;
        }

        String sql = """
                INSERT INTO statements (id, file_type, file_path, instrument_id, review_status, created_at, updated_at)
                VALUES (:id, :fileType, :filePath, :instrumentId, :reviewStatus, :createdAt, :updatedAt)
                ON CONFLICT(id) DO UPDATE SET
                    review_status = excluded.review_status,
                    updated_at    = excluded.updated_at
                """;
        // review_status and updated_at are only two fields mutable on update

        LocalDateTime createdAt = statement.createdAt() != null ? statement.createdAt() : LocalDateTime.now();
        LocalDateTime updatedAt = statement.updatedAt() != null ? statement.updatedAt() : LocalDateTime.now();

        jdbcClient.sql(sql)
                .param("id", statement.id().toString())
                .param("fileType", statement.fileType() != null ? statement.fileType().name() : null)
                .param("filePath", statement.filePath())
                .param("instrumentId", statement.instrumentId())
                .param("reviewStatus", statement.reviewStatus() != null ? statement.reviewStatus().name() : null)
                .param("createdAt", createdAt.toString())
                .param("updatedAt", updatedAt.toString())
                .update();

        log.info("Statement upserted in SQLite: {}", statement.id());
    }
}
