package io.github.harshjoeyit.enrichment.config;

import io.github.harshjoeyit.enrichment.config.EmailEnrichmentConfig.DefaultConfig;
import io.github.harshjoeyit.enrichment.config.EmailEnrichmentConfig.EmailAccountConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailEnrichmentConfigTest {

    @Test
    @DisplayName("Accounts without explicit settings inherit from default-config including folders")
    void accountsWithoutSettings_inheritDefaultConfig() {
        DefaultConfig defaults = new DefaultConfig(
                true, 993, 20,
                List.of("SWIGGY", "AMAZON", "FLIPKART"),
                List.of("INBOX")
        );
        EmailAccountConfig account = new EmailAccountConfig(
                "Personal", "imap.gmail.com", null, null,
                "user@gmail.com", "pass", null, null, null
        );

        EmailEnrichmentConfig config = new EmailEnrichmentConfig(
                true, defaults, List.of(account)
        );

        assertThat(config.defaultConfig().platforms()).containsExactly("SWIGGY", "AMAZON", "FLIPKART");
        assertThat(config.defaultConfig().ssl()).isTrue();
        assertThat(config.defaultConfig().timeoutSeconds()).isEqualTo(20);
        assertThat(config.defaultConfig().port()).isEqualTo(993);
        assertThat(config.defaultConfig().folders()).containsExactly("INBOX");

        assertThat(config.accounts()).hasSize(1);
        EmailAccountConfig enrichedAccount = config.accounts().get(0);
        assertThat(enrichedAccount.platforms()).containsExactly("SWIGGY", "AMAZON", "FLIPKART");
        assertThat(enrichedAccount.port()).isEqualTo(993);
        assertThat(enrichedAccount.ssl()).isTrue();
        assertThat(enrichedAccount.timeoutSeconds()).isEqualTo(20);
        assertThat(enrichedAccount.folders()).containsExactly("INBOX");
    }

    @Test
    @DisplayName("Accounts with custom settings override default-config including folders")
    void accountsWithCustomSettings_overrideDefaultConfig() {
        DefaultConfig defaults = new DefaultConfig(
                true, 993, 15,
                List.of("SWIGGY", "AMAZON", "FLIPKART"),
                List.of("INBOX")
        );
        EmailAccountConfig account = new EmailAccountConfig(
                "Custom", "imap.custom.com", 143, false,
                "user@custom.com", "pass", List.of("SWIGGY"), 30, List.of("[Gmail]/All Mail")
        );

        EmailEnrichmentConfig config = new EmailEnrichmentConfig(
                true, defaults, List.of(account)
        );

        EmailAccountConfig enrichedAccount = config.accounts().get(0);
        assertThat(enrichedAccount.platforms()).containsExactly("SWIGGY");
        assertThat(enrichedAccount.port()).isEqualTo(143);
        assertThat(enrichedAccount.ssl()).isFalse();
        assertThat(enrichedAccount.timeoutSeconds()).isEqualTo(30);
        assertThat(enrichedAccount.folders()).containsExactly("[Gmail]/All Mail");
    }

    @Test
    @DisplayName("Null accounts list defaults to empty list and handles null defaultConfig safely")
    void handlesNullSafely() {
        EmailEnrichmentConfig config = new EmailEnrichmentConfig(false, null, null);

        assertThat(config.enabled()).isFalse();
        assertThat(config.defaultConfig()).isNull();
        assertThat(config.accounts()).isEmpty();
    }
}
