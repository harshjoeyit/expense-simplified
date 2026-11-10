package io.github.harshjoeyit.enrichment.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class EmailParserUtilsTest {

    @Test
    @DisplayName("parsePrice parses rupee symbol, Rs prefix, and plain amounts with commas")
    void parsePrice_parsesValidAmounts() {
        assertThat(EmailParserUtils.parsePrice("₹250.00")).contains(250.0);
        assertThat(EmailParserUtils.parsePrice("Rs. 1,499.50")).contains(1499.50);
        assertThat(EmailParserUtils.parsePrice("INR 99")).contains(99.0);
        assertThat(EmailParserUtils.parsePrice("Total: ₹3,500")).contains(3500.0);
        assertThat(EmailParserUtils.parsePrice("45.75")).contains(45.75);
        assertThat(EmailParserUtils.parsePrice("₨. 1439")).contains(1439.0);
        assertThat(EmailParserUtils.parsePrice("₨. 36334")).contains(36334.0);
        assertThat(EmailParserUtils.parsePrice("₨ 1,234.50")).contains(1234.50);
    }

    @Test
    @DisplayName("parsePrice returns empty on invalid, blank, or null text")
    void parsePrice_returnsEmptyOnInvalid() {
        assertThat(EmailParserUtils.parsePrice(null)).isEmpty();
        assertThat(EmailParserUtils.parsePrice("")).isEmpty();
        assertThat(EmailParserUtils.parsePrice("   ")).isEmpty();
        assertThat(EmailParserUtils.parsePrice("No price here")).isEmpty();
    }

    @Test
    @DisplayName("matchFirst extracts captured group from pattern")
    void matchFirst_extractsGroup() {
        Pattern pattern = Pattern.compile("Order\\s*#?\\s*([A-Z0-9-]+)");

        Optional<String> match = EmailParserUtils.matchFirst("Your Order #OD123456 has shipped", pattern, 1);
        assertThat(match).contains("OD123456");

        Optional<String> noMatch = EmailParserUtils.matchFirst("No match here", pattern, 1);
        assertThat(noMatch).isEmpty();

        assertThat(EmailParserUtils.matchFirst(null, pattern, 1)).isEmpty();
    }

    @Test
    @DisplayName("truncate truncates long text with ellipsis and collapses whitespace")
    void truncate_handlesLengthAndSpacing() {
        assertThat(EmailParserUtils.truncate("Short text", 20)).isEqualTo("Short text");
        assertThat(EmailParserUtils.truncate("This is a very long string exceeding limit", 15))
                .isEqualTo("This is a ve...");
        assertThat(EmailParserUtils.truncate("   Spaced   out   text   ", 30))
                .isEqualTo("Spaced out text");
        assertThat(EmailParserUtils.truncate(null, 10)).isEqualTo("");
    }

    @Test
    @DisplayName("parsePositiveInt parses valid positive integers and ignores surrounding text")
    void parsePositiveInt_parsesCorrectly() {
        assertThat(EmailParserUtils.parsePositiveInt("3")).hasValue(3);
        assertThat(EmailParserUtils.parsePositiveInt(" 12 ")).hasValue(12);
        assertThat(EmailParserUtils.parsePositiveInt("Qty: 2")).hasValue(2);
        assertThat(EmailParserUtils.parsePositiveInt("0")).isEmpty();
        assertThat(EmailParserUtils.parsePositiveInt("-5")).hasValue(5); // strips non-digits to 5, positive
        assertThat(EmailParserUtils.parsePositiveInt("abc")).isEmpty();
        assertThat(EmailParserUtils.parsePositiveInt(null)).isEmpty();
        assertThat(EmailParserUtils.parsePositiveInt("   ")).isEmpty();
    }

    @Test
    @DisplayName("parseQuantity returns parsed quantity within bounds or default")
    void parseQuantity_respectsBoundsAndDefaults() {
        assertThat(EmailParserUtils.parseQuantity("2")).isEqualTo(2);
        assertThat(EmailParserUtils.parseQuantity("10")).isEqualTo(10);
        assertThat(EmailParserUtils.parseQuantity(null)).isEqualTo(1);
        assertThat(EmailParserUtils.parseQuantity("")).isEqualTo(1);
        assertThat(EmailParserUtils.parseQuantity("invalid")).isEqualTo(1);
        assertThat(EmailParserUtils.parseQuantity("0", 1)).isEqualTo(1);
        assertThat(EmailParserUtils.parseQuantity("99999", 1)).isEqualTo(1); // Exceeds upper bound (1000)
        assertThat(EmailParserUtils.parseQuantity("5", 2)).isEqualTo(5);
    }
}
