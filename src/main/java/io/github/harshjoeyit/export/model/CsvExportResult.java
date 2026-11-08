package io.github.harshjoeyit.export.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record CsvExportResult(
        int exportedTxns,
        Map<ExportExclusionReason, Integer> excludedTxns,
        List<Path> exportedFiles
) {
    public CsvExportResult {
        excludedTxns = excludedTxns == null ? Map.of() : Map.copyOf(excludedTxns);
        exportedFiles = exportedFiles == null ? List.of() : List.copyOf(exportedFiles);
    }

    public boolean hasExcluded() {
        return excludedTxnsCount() > 0;
    }

    public int excludedTxnsCount() {
        return excludedTxns.values().stream().mapToInt(Integer::intValue).sum();
    }
}
