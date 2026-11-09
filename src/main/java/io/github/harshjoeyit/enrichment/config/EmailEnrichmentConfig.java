package io.github.harshjoeyit.enrichment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.plugins.email-enrichment")
public record EmailEnrichmentConfig(
        boolean enabled,
        DefaultConfig defaultConfig,
        List<EmailAccountConfig> accounts
) {
    public EmailEnrichmentConfig {
        if (accounts == null) {
            accounts = List.of();
        } else if (defaultConfig != null) {
            accounts = accounts.stream()
                    .map(acc -> acc.withDefaults(defaultConfig))
                    .toList();
        }
    }

    public record DefaultConfig(
            Boolean ssl,
            Integer port,
            Integer timeoutSeconds,
            List<String> platforms,
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
        public EmailAccountConfig withDefaults(DefaultConfig defaults) {
            if (defaults == null) {
                return this;
            }
            Boolean finalSsl = this.ssl != null ? this.ssl : defaults.ssl();
            Integer finalPort = this.port != null ? this.port : defaults.port();
            Integer finalTimeout = this.timeoutSeconds != null ? this.timeoutSeconds : defaults.timeoutSeconds();
            List<String> finalPlatforms = (this.platforms != null && !this.platforms.isEmpty())
                    ? this.platforms
                    : defaults.platforms();
            List<String> finalFolders = (this.folders != null && !this.folders.isEmpty())
                    ? this.folders
                    : defaults.folders();

            return new EmailAccountConfig(
                    this.name, this.host, finalPort, finalSsl,
                    this.username, this.password, finalPlatforms, finalTimeout, finalFolders
            );
        }
    }
}
