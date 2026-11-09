package io.github.harshjoeyit.enrichment.parser;

import io.github.harshjoeyit.enrichment.model.OrderReceipt;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailReceiptParser {

    String platform();

    boolean supports(String sender, String subject);

    Optional<OrderReceipt> parse(String sender, String subject, String bodyContent, LocalDateTime emailDate);
}
