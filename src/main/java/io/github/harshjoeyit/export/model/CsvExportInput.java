package io.github.harshjoeyit.export.model;

import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

@Builder
public record CsvExportInput(
        UUID statementId,
        LocalDate periodStartDate,
        LocalDate periodEndDate
){}
