package io.github.harshjoeyit.enrichment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.plugins.email-enrichment")
public record EmailEnrichmentConfig(
        boolean enabled,
        DefaultConfig defaultConfig,

        // platform -> aliases
        Map<String, List<String>> platforms,
        List<EmailAccountConfig> accounts
) {
    public EmailEnrichmentConfig {
        if (platforms == null) {
            platforms = Map.of();
        }
        if (accounts == null) {
            accounts = List.of();
        } else {
            List<String> defaultAccountPlatforms = List.copyOf(platforms.keySet());
            accounts = accounts.stream()
                    .map(acc -> acc.withDefaults(defaultConfig, defaultAccountPlatforms))
                    .toList();
        }
    }

    public record DefaultConfig(
            Boolean ssl,
            Integer port,
            Integer timeoutSeconds,
            List<String> folders
    ) {}

    public record EmailAccountConfig(
            String name,
            String host,
            Integer port,
            Boolean ssl,
            String username,
            String password,
            List<String> platforms,
            Integer timeoutSeconds,
            List<String> folders
    ) {
        public EmailAccountConfig withDefaults(DefaultConfig defaults, List<String> defaultPlatforms) {
            Boolean finalSsl = (this.ssl != null) ? this.ssl : (defaults != null ? defaults.ssl() : null);
            Integer finalPort = (this.port != null) ? this.port : (defaults != null ? defaults.port() : null);
            Integer finalTimeout = (this.timeoutSeconds != null) ? this.timeoutSeconds : (defaults != null ? defaults.timeoutSeconds() : null);
            List<String> finalFolders = (this.folders != null && !this.folders.isEmpty())
                    ? this.folders
                    : (defaults != null ? defaults.folders() : null);
            List<String> finalPlatforms = (this.platforms != null && !this.platforms.isEmpty())
                    ? this.platforms
                    : defaultPlatforms;

            return new EmailAccountConfig(
                    this.name, this.host, finalPort, finalSsl,
                    this.username, this.password, finalPlatforms, finalTimeout, finalFolders
            );
        }
    }
}
