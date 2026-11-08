package io.github.harshjoeyit.export.model;

import lombok.Getter;

@Getter
public enum ExportExclusionReason {
    PENDING_REVIEW("Pending user review");

    private final String description;

    ExportExclusionReason(String description) {
        this.description = description;
    }
}