package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.Statement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatementRepository {
    Optional<Statement> findById(UUID statementId);
    Optional<Statement> findLatest();
    List<Statement> findAll();
    void saveStatement(Statement statement);
}
