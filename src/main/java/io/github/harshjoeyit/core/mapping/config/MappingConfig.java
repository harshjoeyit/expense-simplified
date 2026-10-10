package io.github.harshjoeyit.core.mapping.config;

import io.github.harshjoeyit.core.mapping.model.MappingRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "mapping")
public record MappingConfig(
    Map<String, MappingRule> counterpartyIdRules,
    Map<String, MappingRule> counterpartyNameRules
){}
