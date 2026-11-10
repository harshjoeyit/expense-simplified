package io.github.harshjoeyit.enrichment.matcher;

import io.github.harshjoeyit.enrichment.config.EmailEnrichmentConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Implementation of {@link PlatformAliasProvider} backed by configuration properties
 * or custom platform-to-aliases mappings.
 */
@Component
public class ConfigPlatformAliasProvider implements PlatformAliasProvider {

    private final Map<String, List<String>> aliases = new HashMap<>();

    public ConfigPlatformAliasProvider() {
        this((EmailEnrichmentConfig) null);
    }

    @Autowired(required = false)
    public ConfigPlatformAliasProvider(EmailEnrichmentConfig config) {
        if (config != null && config.platforms() != null) {
            config.platforms().forEach((k, v) -> {
                if (k != null && v != null) {
                    aliases.put(k.toUpperCase(Locale.ROOT),
                            v.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList());
                }
            });
        }
    }

    public ConfigPlatformAliasProvider(Map<String, List<String>> customAliases) {
        if (customAliases != null) {
            customAliases.forEach((k, v) -> {
                if (k != null && v != null) {
                    aliases.put(k.toUpperCase(Locale.ROOT),
                            v.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList());
                }
            });
        }
    }

    @Override
    public List<String> getAliases(String platform) {
        if (platform == null) {
            return Collections.emptyList();
        }
        String key = platform.toUpperCase(Locale.ROOT);
        return aliases.getOrDefault(key, List.of(key));
    }
}
