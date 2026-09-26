package io.github.harshjoeyit.core.parse;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Component
public class NarrationTooling {
    // UTR is bank reference number / Txn ID for UPI Txns
    String UpiUtrRegex = "(?<!\\d)\\d{12}(?!\\d)";
    String BankIfscRegex = "\\b[A-Z]{4}0[A-Z0-9]{6}\\b";
    String UpiIdRegex = "(^[a-zA-Z0-9._-]+@[a-zA-Z]{2,64})";

    public String extract12DigUpiUtr(String text) {
        return extractMatchingString(text, UpiUtrRegex);
    }

    public String extractBankIfsc(String text) {
        return extractMatchingString(text, BankIfscRegex);
    }

    // Extract UPI from a string which starts with UPI ID.
    public String extractPrefixUpiId(String text) {
        return extractMatchingString(text, UpiIdRegex);
    }

    private String extractMatchingString(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(0);
        }

        return null;
    }
}
