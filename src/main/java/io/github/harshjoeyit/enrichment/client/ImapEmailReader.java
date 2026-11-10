package io.github.harshjoeyit.enrichment.client;

import io.github.harshjoeyit.enrichment.config.EmailEnrichmentConfig.EmailAccountConfig;
import io.github.harshjoeyit.enrichment.model.OrderReceipt;
import io.github.harshjoeyit.enrichment.parser.EmailReceiptParser;
import jakarta.mail.*;
import jakarta.mail.search.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Component
public class ImapEmailReader {

    private final List<EmailReceiptParser> parsers;

    public ImapEmailReader(List<EmailReceiptParser> parsers) {
        this.parsers = parsers != null ? parsers : Collections.emptyList();
    }

    public List<OrderReceipt> fetchReceipts(EmailAccountConfig account, LocalDate startDate, LocalDate endDate) {
        List<String> senderKeywords = (account != null && account.platforms() != null)
                ? account.platforms()
                : Collections.emptyList();
        return fetchReceipts(account, startDate, endDate, senderKeywords);
    }

    public List<OrderReceipt> fetchReceipts(
            EmailAccountConfig account,
            LocalDate startDate,
            LocalDate endDate,
            List<String> senderSearchStrings
    ) {
        if (account == null || account.username() == null || account.password() == null) {
            log.warn("Email account configuration incomplete, skipping IMAP fetch");
            return Collections.emptyList();
        }

        if (account.folders() == null || account.folders().isEmpty()) {
            log.warn("No folders configured for account [{}], skipping IMAP fetch", account.name());
            return Collections.emptyList();
        }

        log.info("Connecting to IMAP [{}] for user: {} (range: {} to {}, senders: {})",
                account.host(), account.username(), startDate, endDate, senderSearchStrings);

        Properties props = new Properties();

        String protocol = Boolean.TRUE.equals(account.ssl()) ? "imaps" : "imap";
        props.put("mail." + protocol + ".host", account.host());
        props.put("mail." + protocol + ".port", String.valueOf(account.port()));
        props.put("mail." + protocol + ".ssl.enable", String.valueOf(account.ssl()));

        int timeoutMs = account.timeoutSeconds() != null ? account.timeoutSeconds() * 1000 : 15000;
        props.put("mail." + protocol + ".connectiontimeout", String.valueOf(timeoutMs));
        props.put("mail." + protocol + ".timeout", String.valueOf(timeoutMs));

        Session session = Session.getInstance(props);
        Store store = null;
        Map<String, OrderReceipt> receiptsByKey = new LinkedHashMap<>();

        try {
            store = session.getStore(protocol);
            store.connect(account.host(), account.username(), account.password());

            for (String folderName : account.folders()) {
                Folder folder = null;
                try {
                    folder = store.getFolder(folderName);
                    if (!folder.exists()) {
                        log.debug("Folder [{}] does not exist on IMAP server for account [{}], skipping",
                                folderName, account.name());
                        continue;
                    }
                    folder.open(Folder.READ_ONLY);

                    SearchTerm term = buildSearchTerm(startDate, endDate, senderSearchStrings);
                    if (term == null) {
                        log.warn("No search criteria provided for account [{}], skipping folder [{}] " +
                                        "to prevent full mailbox scan", account.name(), folderName);
                        continue;
                    }
                    Message[] messages = folder.search(term);

                    log.info("Found {} candidate messages in folder [{}] for account [{}]",
                            messages.length, folderName, account.name());

                    for (Message msg : messages) {
                        try {
                            OrderReceipt receipt = processMessage(msg);
                            if (receipt != null) {
                                String receiptKey = buildReceiptDeduplicationKey(receipt);
                                OrderReceipt existing = receiptsByKey.putIfAbsent(receiptKey, receipt);
                                if (existing != null) {
                                    log.debug("Skipping duplicate receipt [{}] in folder [{}] (already captured)",
                                            receiptKey, folderName);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Failed to process email message in folder [{}]: {}", folderName, e.getMessage());
                        }
                    }
                } finally {
                    if (folder != null && folder.isOpen()) {
                        try {
                            folder.close(false);
                        } catch (Exception ignored) {}
                    }
                }
            }

        } catch (Exception e) {
            log.error("IMAP connection/query failed for account [{}]: {}", account.name(), e.getMessage());
            throw new RuntimeException("IMAP query failed: " + e.getMessage(), e);
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (Exception ignored) {}
            }
        }

        log.info("Successfully extracted {} order receipts from account [{}] across folders {}",
                receiptsByKey.size(), account.name(), account.folders());
        return new ArrayList<>(receiptsByKey.values());
    }

    SearchTerm buildSearchTerm(LocalDate startDate, LocalDate endDate, List<String> senderSearchStrings) {
        List<SearchTerm> terms = new ArrayList<>();

        if (startDate != null) {
            Date start = Date.from(startDate.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            terms.add(new SentDateTerm(ComparisonTerm.GE, start));
        }

        if (endDate != null) {
            Date end = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            terms.add(new SentDateTerm(ComparisonTerm.LE, end));
        }

        SearchTerm dateTerm = null;
        if (!terms.isEmpty()) {
            dateTerm = terms.size() == 1 ? terms.get(0) : new AndTerm(terms.toArray(new SearchTerm[0]));
        }

        SearchTerm senderTerm = null;
        if (senderSearchStrings != null && !senderSearchStrings.isEmpty()) {
            SearchTerm[] senderTerms = senderSearchStrings.stream()
                    .filter(kw -> kw != null && !kw.isBlank())
                    .map(kw -> new FromStringTerm(kw.toLowerCase(Locale.ROOT).trim()))
                    .toArray(SearchTerm[]::new);
            if (senderTerms.length == 1) {
                senderTerm = senderTerms[0];
            } else if (senderTerms.length > 1) {
                senderTerm = new OrTerm(senderTerms);
            }
        }

        if (dateTerm != null && senderTerm != null) {
            return new AndTerm(dateTerm, senderTerm);
        } else if (dateTerm != null) {
            return dateTerm;
        } else {
            return senderTerm;
        }
    }

    /**
     * Key = platform:orderId or platform:amount:date
     */
    private String buildReceiptDeduplicationKey(OrderReceipt receipt) {
        if (receipt.platformOrderId() != null && !receipt.platformOrderId().isBlank()) {
            return receipt.platform() + ":" + receipt.platformOrderId();
        }
        return receipt.platform() + ":" + receipt.totalAmount() + ":" + receipt.orderDateTime();
    }

    OrderReceipt processMessage(Message msg) throws Exception {
        String subject = msg.getSubject() != null ? msg.getSubject() : "";
        Address[] fromAddresses = msg.getFrom();
        String sender = (fromAddresses != null && fromAddresses.length > 0) ? fromAddresses[0].toString() : "";

        EmailReceiptParser matchedParser = null;
        for (EmailReceiptParser parser : parsers) {
            if (parser.supports(sender, subject)) {
                matchedParser = parser;
                break;
            }
        }

        if (matchedParser == null) {
            return null;
        }

        Date emailDate = msg.getSentDate();
        if (emailDate == null) {
            emailDate = msg.getReceivedDate();
        }
        if (emailDate == null) {
            log.warn("Skipping email '{}' from '{}': no sent or received date found", subject, sender);
            return null;
        }

        LocalDateTime emailDateTime = LocalDateTime.ofInstant(emailDate.toInstant(), ZoneId.systemDefault());

        String body = extractBody(msg);
        return matchedParser.parse(sender, subject, body, emailDateTime).orElse(null);
    }

    public static String extractBody(Part part) throws Exception {
        if (part == null) {
            log.warn("Could not extract email body since input was null");
            return "";
        }
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/alternative")) {
            Multipart mp = (Multipart) part.getContent();
            String textFallback = null;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/html")) {
                    return (String) bp.getContent();
                } else if (bp.isMimeType("text/plain")) {
                    textFallback = (String) bp.getContent();
                }
            }
            return textFallback != null ? textFallback : "";
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                // Skip the attachments
                if (!Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) {
                    sb.append(extractBody(bp));
                }
            }
            return sb.toString();
        }

        log.warn("Could not extract email body since mime type did not match text, html or multipart: {}"
                , part.getContentType());

        return "";
    }
}
