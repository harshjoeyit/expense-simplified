package io.github.harshjoeyit.core.parse.util;

public class ParseUtil {
    public static double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(amountStr.trim().replace(",", ""));
    }
}
