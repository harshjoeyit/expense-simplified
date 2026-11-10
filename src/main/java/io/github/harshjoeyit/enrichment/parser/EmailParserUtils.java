package io.github.harshjoeyit.enrichment.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailParserUtils {

    private static final Logger log = LoggerFactory.getLogger(EmailParserUtils.class);

    // Triggers if it sees ₹1000 or Rs. 1000.
    private static final Pattern PRICE_WITH_CURRENCY = Pattern.compile(
            "(?i)(?:[₹₨]\\.?|Rs\\.?|INR)\\s*([0-9]{1,3}(?:,[0-9]{2,3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    );

    // Trigger if it sees amounts as independent cell values like "199.00"
    private static final Pattern STANDALONE_PRICE = Pattern.compile(
            "^\\s*(?:[₹₨]\\.?|Rs\\.?|INR)?\\s*([0-9]{1,3}(?:,[0-9]{2,3})+(?:\\.[0-9]{1,2})?|[0-9]{1,6}(?:\\.[0-9]{1,2})?)\\s*$"
    );

    private EmailParserUtils() {}

    public static Optional<Double> parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String trimmed = text.trim();

        // Look for explicit currency symbol (₹, Rs, INR)
        Matcher currencyMatcher = PRICE_WITH_CURRENCY.matcher(trimmed);
        if (currencyMatcher.find()) {
            try {
                String cleanNumber = currencyMatcher.group(1).replace(",", "");
                return Optional.of(Double.parseDouble(cleanNumber));
            } catch (NumberFormatException e) {
                log.debug("Failed to parse price number from currency match '{}': {}", trimmed, e.getMessage());
            }
        }

        // Standalone number (e.g. table cell without currency prefix)
        Matcher standaloneMatcher = STANDALONE_PRICE.matcher(trimmed);
        if (standaloneMatcher.matches()) {
            try {
                String cleanNumber = standaloneMatcher.group(1).replace(",", "");
                return Optional.of(Double.parseDouble(cleanNumber));
            } catch (NumberFormatException e) {
                log.debug("Failed to parse price number from standalone match '{}': {}", trimmed, e.getMessage());
            }
        }

        return Optional.empty();
    }

    public static OptionalInt parsePositiveInt(String text) {
        if (text == null || text.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            String clean = text.replaceAll("[^0-9]", "");
            if (clean.isBlank()) {
                return OptionalInt.empty();
            }
            int val = Integer.parseInt(clean);
            return val > 0 ? OptionalInt.of(val) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            log.debug("Failed to parse positive integer from '{}': {}", text, e.getMessage());
            return OptionalInt.empty();
        }
    }

    public static int parseQuantity(String text, int defaultQty) {
        OptionalInt val = parsePositiveInt(text);
        if (val.isPresent() && val.getAsInt() <= 1000) {
            return val.getAsInt();
        }
        return defaultQty;
    }

    public static int parseQuantity(String text) {
        return parseQuantity(text, 1);
    }

    public static Optional<String> matchFirst(String text, Pattern pattern, int group) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(text);
        if (matcher.find() && matcher.groupCount() >= group) {
            String val = matcher.group(group);
            return val != null && !val.isBlank() ? Optional.of(val.trim()) : Optional.empty();
        }
        return Optional.empty();
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() <= maxLength) {
            return clean;
        }
        return clean.substring(0, maxLength - 3) + "...";
    }
}
