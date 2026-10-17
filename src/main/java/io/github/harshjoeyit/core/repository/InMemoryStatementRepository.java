package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.Statement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InMemoryStatementRepository implements StatementRepository {

    private final Map<UUID, Statement> statements = new ConcurrentHashMap<>();

    @Override
    public Optional<Statement> findById(UUID statementId) {
        if (statementId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(statements.get(statementId));
    }

    @Override
    public Optional<Statement> findLatest() {
        return statements.values()
                .stream()
                .max(Comparator.comparing(Statement::uploadTime));
    }

    @Override
    public void saveStatement(Statement statement) {
        if (statement != null && statement.id() != null) {
            this.statements.put(statement.id(), statement);
            log.info("statement created: {}", statement);
        }
    }
}
