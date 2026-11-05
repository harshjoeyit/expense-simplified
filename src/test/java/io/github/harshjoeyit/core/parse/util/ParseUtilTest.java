package io.github.harshjoeyit.core.parse.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ParseUtilTest {

    @Test
    void parseAmount_withCommasAndSpaces_parsesCorrectly() {
        assertThat(ParseUtil.parseAmount("1,234.56")).isEqualTo(1234.56);
        assertThat(ParseUtil.parseAmount("  500.00 ")).isEqualTo(500.0);
        assertThat(ParseUtil.parseAmount("")).isEqualTo(0.0);
        assertThat(ParseUtil.parseAmount(null)).isEqualTo(0.0);
    }

    @Test
    void matchesHeaders_caseInsensitiveAndTrimmed_matches() {
        Set<String> actual = Set.of("Date ", "Narration", "VALUE DAT", "Debit Amount");
        Set<String> expected = Set.of("date", "narration", "value dat", "debit amount");

        assertThat(ParseUtil.matchesHeaders(actual, expected)).isTrue();
    }

    @Test
    void matchesHeaders_differentSizeOrMismatch_returnsFalse() {
        Set<String> actual = Set.of("Date", "Narration");
        Set<String> expected = Set.of("Date", "Narration", "Amount");

        assertThat(ParseUtil.matchesHeaders(actual, expected)).isFalse();
        assertThat(ParseUtil.matchesHeaders(null, expected)).isFalse();
    }

    @Test
    void getField_caseInsensitiveRetrieval_works() throws IOException {
        String csv = "Date,Description,AMT\n01/08/2026,Swiggy,500.00";
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().get();

        try (CSVParser parser = format.parse(new StringReader(csv))) {
            CSVRecord record = parser.iterator().next();

            assertThat(ParseUtil.getField(record, "date")).isEqualTo("01/08/2026");
            assertThat(ParseUtil.getField(record, "DESCRIPTION")).isEqualTo("Swiggy");
            assertThat(ParseUtil.getField(record, "amt")).isEqualTo("500.00");
            assertThat(ParseUtil.getField(record, "non_existent")).isNull();
        }
    }
}
