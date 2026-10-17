package io.github.harshjoeyit.core.model;

import io.github.harshjoeyit.cli.command.StatementType;
import lombok.Builder;

/**
 * @param id User provided identifier. Intended to be short. Ex - hdfc-sal-acc, hdfc-cc-mil
 */
@Builder
public record Instrument (
        String id,
        String description,
        String maskedNum,
        StatementType statementType,
        String userId
) {}
