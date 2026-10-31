package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.cli.command.StatementType;
import io.github.harshjoeyit.config.InstrumentConfig;
import io.github.harshjoeyit.core.model.Instrument;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@ConditionalOnProperty(name = "app.persistence", havingValue = "sqlite", matchIfMissing = true)
public class JdbcInstrumentRepository implements InstrumentRepository {

    private final JdbcClient jdbcClient;
    private final InstrumentConfig instrumentConfig;

    private static final RowMapper<Instrument> ROW_MAPPER = (rs, rowNum) -> {
        String statementTypeStr = rs.getString("statement_type");
        StatementType statementType = statementTypeStr != null ? StatementType.valueOf(statementTypeStr) : null;

        return Instrument.builder()
                .id(rs.getString("id"))
                .description(rs.getString("description"))
                .maskedNum(rs.getString("masked_num"))
                .statementType(statementType)
                .userId(rs.getString("user_id"))
                .build();
    };

    public JdbcInstrumentRepository(JdbcClient jdbcClient, InstrumentConfig instrumentConfig) {
        this.jdbcClient = jdbcClient;
        this.instrumentConfig = instrumentConfig;
    }

    @PostConstruct
    public void init() {
        if (instrumentConfig != null && instrumentConfig.instruments() != null) {
            log.info("Syncing {} instruments from config into SQLite (inserting new only)", instrumentConfig.instruments().size());
            String sql = """
                    INSERT INTO instruments (id, description, masked_num, statement_type, user_id, created_at, updated_at)
                    VALUES (:id, :description, :maskedNum, :statementType, :userId, :createdAt, :updatedAt)
                    ON CONFLICT(id) DO NOTHING
                    """;

            String now = LocalDateTime.now().toString();
            for (Instrument instrument : instrumentConfig.instruments()) {
                if (instrument != null && instrument.id() != null) {
                    jdbcClient.sql(sql)
                            .param("id", instrument.id())
                            .param("description", instrument.description())
                            .param("maskedNum", instrument.maskedNum())
                            .param("statementType", instrument.statementType() != null ? instrument.statementType().name() : null)
                            .param("userId", instrument.userId())
                            .param("createdAt", now)
                            .param("updatedAt", now)
                            .update();
                }
            }
        }
    }

    @Override
    public Optional<Instrument> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbcClient.sql("SELECT * FROM instruments WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public List<Instrument> findByUserId(String userId) {
        if (userId != null) {
            return jdbcClient.sql("SELECT * FROM instruments WHERE user_id = :userId")
                    .param("userId", userId)
                    .query(ROW_MAPPER)
                    .list();
        }
        return jdbcClient.sql("SELECT * FROM instruments")
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public void saveInstrument(Instrument instrument) {
        if (instrument == null || instrument.id() == null) {
            log.warn("Cannot save null instrument or instrument without ID");
            return;
        }

        String sql = """
                INSERT INTO instruments (id, description, masked_num, statement_type, user_id, created_at, updated_at)
                VALUES (:id, :description, :maskedNum, :statementType, :userId, :createdAt, :updatedAt)
                ON CONFLICT(id) DO UPDATE SET
                    description    = excluded.description,
                    masked_num     = excluded.masked_num,
                    statement_type = excluded.statement_type,
                    user_id        = excluded.user_id,
                    updated_at     = excluded.updated_at
                """;

        String now = LocalDateTime.now().toString();

        jdbcClient.sql(sql)
                .param("id", instrument.id())
                .param("description", instrument.description())
                .param("maskedNum", instrument.maskedNum())
                .param("statementType", instrument.statementType() != null ? instrument.statementType().name() : null)
                .param("userId", instrument.userId())
                .param("createdAt", now)
                .param("updatedAt", now)
                .update();

        log.debug("Instrument upserted in SQLite: {}", instrument.id());
    }
}
