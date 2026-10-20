package io.github.harshjoeyit.export.config;

import io.github.harshjoeyit.core.model.TxnCategory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

// Root config
@ConfigurationProperties
public record ExportConfig (
        String outputDir,
        Map<String, Profile> profiles
) {
    // An individual profile (e.g. 'notion', 'obsidian')
    public record Profile(
            String dateFormat,
            MultiFileColumnMappingConfig multi,
            ColumnMappingConfig single,
            Map<TxnCategory, String> categoryMapping
    ) {
        public boolean isMulti() {
            return this.multi != null;
        }
    }

    // Multi export column mapping config
    public record MultiFileColumnMappingConfig(
            ColumnMappingConfig purchase,
            ColumnMappingConfig income,
            ColumnMappingConfig investment,
            ColumnMappingConfig lendOrBorrowed
    ) {}

    // Column mapping config
    public record ColumnMappingConfig(
            LinkedHashMap<String, String> columns
    ) {}
}

