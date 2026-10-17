package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.Statement;

import java.util.Optional;
import java.util.UUID;

public interface StatementRepository {
    Optional<Statement> findById(UUID statementId);
    Optional<Statement> findLatest();
    void saveStatement(Statement statement);
}
