package io.github.harshjoeyit.core.parse.util;

import org.apache.commons.csv.CSVRecord;

import java.util.Set;
import java.util.stream.Collectors;

public class ParseUtil {

    public static double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(amountStr.trim().replace(",", ""));
    }

    public static String getField(CSVRecord record, String fieldName) {
        if (record == null || fieldName == null) {
            return null;
        }
        if (record.isMapped(fieldName)) {
            return record.get(fieldName);
        }
        if (record.getParser() != null && record.getParser().getHeaderMap() != null) {
            for (String header : record.getParser().getHeaderMap().keySet()) {
                if (header.trim().equalsIgnoreCase(fieldName.trim())) {
                    return record.get(header);
                }
            }
        }
        return null;
    }

    public static boolean matchesHeaders(Set<String> actualHeaders, Set<String> expectedHeaders) {
        if (actualHeaders == null || expectedHeaders == null) {
            return false;
        }
        if (actualHeaders.size() != expectedHeaders.size()) {
            return false;
        }
        Set<String> normalizedActual = actualHeaders.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        Set<String> normalizedExpected = expectedHeaders.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        return normalizedActual.equals(normalizedExpected);
    }
}
