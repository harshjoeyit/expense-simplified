package io.github.harshjoeyit.enrichment.client;

import io.github.harshjoeyit.enrichment.config.EmailEnrichmentConfig.EmailAccountConfig;
import io.github.harshjoeyit.enrichment.model.OrderReceipt;
import io.github.harshjoeyit.enrichment.parser.EmailReceiptParser;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImapEmailReaderTest {

    private final ImapEmailReader reader = new ImapEmailReader(Collections.emptyList());

    @Test
    @DisplayName("buildSearchTerm combines date range and multiple sender search strings")
    void buildSearchTerm_withDatesAndSenders_buildsCombinedTerm() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        List<String> senders = List.of("SWIGGY", "AMAZON", "FLIPKART");

        SearchTerm term = reader.buildSearchTerm(start, end, senders);

        assertThat(term).isInstanceOf(AndTerm.class);
        AndTerm andTerm = (AndTerm) term;
        assertThat(andTerm.getTerms()).hasSize(2);
        assertThat(andTerm.getTerms()[1]).isInstanceOf(OrTerm.class);
    }

    @Test
    @DisplayName("buildSearchTerm with single sender does not wrap in OrTerm")
    void buildSearchTerm_withSingleSender_usesDirectFromStringTerm() {
        SearchTerm term = reader.buildSearchTerm(null, null, List.of("SWIGGY"));

        assertThat(term).isInstanceOf(FromStringTerm.class);
        FromStringTerm fromTerm = (FromStringTerm) term;
        assertThat(fromTerm.getPattern()).isEqualTo("swiggy");
    }

    @Test
    @DisplayName("buildSearchTerm with no senders produces only date terms")
    void buildSearchTerm_withNoSenders_producesOnlyDateTerm() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        SearchTerm term = reader.buildSearchTerm(start, null, null);

        assertThat(term).isNotNull();
        assertThat(term).isNotInstanceOf(OrTerm.class);
    }

    @Test
    @DisplayName("fetchReceipts returns empty list if account credentials are incomplete")
    void fetchReceipts_withIncompleteAccount_returnsEmptyList() {
        EmailAccountConfig account = new EmailAccountConfig(
                "Incomplete", "imap.gmail.com", 993, true,
                null, null, List.of("SWIGGY"), 15, List.of("INBOX")
        );

        List<OrderReceipt> receipts = reader.fetchReceipts(account, LocalDate.now(), LocalDate.now());
        assertThat(receipts).isEmpty();
    }

    @Test
    @DisplayName("fetchReceipts returns empty list if account has no folders configured")
    void fetchReceipts_withNoFolders_returnsEmptyList() {
        EmailAccountConfig account = new EmailAccountConfig(
                "NoFolders", "imap.gmail.com", 993, true,
                "user@gmail.com", "pass", List.of("SWIGGY"), 15, List.of()
        );

        List<OrderReceipt> receipts = reader.fetchReceipts(account, LocalDate.now(), LocalDate.now());
        assertThat(receipts).isEmpty();
    }

    @Test
    @DisplayName("extractBody extracts content from text/html part")
    void extractBody_fromHtmlPart() throws Exception {
        Part part = mock(Part.class);
        when(part.isMimeType("text/html")).thenReturn(true);
        when(part.getContent()).thenReturn("<html>Hello</html>");

        String body = ImapEmailReader.extractBody(part);
        assertThat(body).isEqualTo("<html>Hello</html>");
    }

    @Test
    @DisplayName("extractBody extracts content from multipart part")
    void extractBody_fromMultipart() throws Exception {
        Part rootPart = mock(Part.class);
        Multipart multipart = mock(Multipart.class);
        BodyPart htmlPart = mock(BodyPart.class);

        when(rootPart.isMimeType("multipart/*")).thenReturn(true);
        when(rootPart.getContent()).thenReturn(multipart);
        when(multipart.getCount()).thenReturn(1);
        when(multipart.getBodyPart(0)).thenReturn(htmlPart);
        when(htmlPart.isMimeType("text/html")).thenReturn(true);
        when(htmlPart.getContent()).thenReturn("<p>Order Confirmed</p>");

        String body = ImapEmailReader.extractBody(rootPart);
        assertThat(body).isEqualTo("<p>Order Confirmed</p>");
    }

    @Test
    @DisplayName("processMessage uses sentDate when present")
    void processMessage_withSentDate() throws Exception {
        EmailReceiptParser parser = mock(EmailReceiptParser.class);
        when(parser.supports(any(), any())).thenReturn(true);
        when(parser.parse(any(), any(), any(), any())).thenAnswer(inv -> {
            LocalDateTime dt = inv.getArgument(3);
            return Optional.of(OrderReceipt.builder()
                    .platform("TEST")
                    .totalAmount(100.0)
                    .orderDateTime(dt)
                    .build());
        });

        ImapEmailReader readerWithParser = new ImapEmailReader(List.of(parser));
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("Test Order");
        when(msg.getFrom()).thenReturn(new Address[]{new InternetAddress("test@test.com")});
        when(msg.isMimeType("text/plain")).thenReturn(true);
        when(msg.getContent()).thenReturn("Test content");

        Date sentDate = Date.from(Instant.parse("2026-05-10T10:00:00Z"));
        when(msg.getSentDate()).thenReturn(sentDate);

        OrderReceipt receipt = readerWithParser.processMessage(msg);
        assertThat(receipt).isNotNull();
        assertThat(receipt.orderDateTime()).isEqualTo(LocalDateTime.ofInstant(sentDate.toInstant(), ZoneId.systemDefault()));
    }

    @Test
    @DisplayName("processMessage falls back to receivedDate when sentDate is null")
    void processMessage_withNullSentDate_fallsBackToReceivedDate() throws Exception {
        EmailReceiptParser parser = mock(EmailReceiptParser.class);
        when(parser.supports(any(), any())).thenReturn(true);
        when(parser.parse(any(), any(), any(), any())).thenAnswer(inv -> {
            LocalDateTime dt = inv.getArgument(3);
            return Optional.of(OrderReceipt.builder()
                    .platform("TEST")
                    .totalAmount(100.0)
                    .orderDateTime(dt)
                    .build());
        });

        ImapEmailReader readerWithParser = new ImapEmailReader(List.of(parser));
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("Test Order");
        when(msg.getFrom()).thenReturn(new Address[]{new InternetAddress("test@test.com")});
        when(msg.isMimeType("text/plain")).thenReturn(true);
        when(msg.getContent()).thenReturn("Test content");

        Date receivedDate = Date.from(Instant.parse("2026-05-11T12:00:00Z"));
        when(msg.getSentDate()).thenReturn(null);
        when(msg.getReceivedDate()).thenReturn(receivedDate);

        OrderReceipt receipt = readerWithParser.processMessage(msg);
        assertThat(receipt).isNotNull();
        assertThat(receipt.orderDateTime()).isEqualTo(LocalDateTime.ofInstant(receivedDate.toInstant(), ZoneId.systemDefault()));
    }

    @Test
    @DisplayName("processMessage returns null when both sentDate and receivedDate are null")
    void processMessage_withBothDatesNull_returnsNull() throws Exception {
        EmailReceiptParser parser = mock(EmailReceiptParser.class);
        when(parser.supports(any(), any())).thenReturn(true);

        ImapEmailReader readerWithParser = new ImapEmailReader(List.of(parser));
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("Test Order");
        when(msg.getFrom()).thenReturn(new Address[]{new InternetAddress("test@test.com")});
        when(msg.getSentDate()).thenReturn(null);
        when(msg.getReceivedDate()).thenReturn(null);

        OrderReceipt receipt = readerWithParser.processMessage(msg);
        assertThat(receipt).isNull();
    }
}
