package io.github.harshjoeyit.enrichment.parser;

import io.github.harshjoeyit.enrichment.client.ImapEmailReader;
import io.github.harshjoeyit.enrichment.model.OrderReceipt;
import io.github.harshjoeyit.enrichment.model.OrderReceiptItem;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AmazonEmailParserTest {

    private AmazonEmailParser parser;

    @BeforeEach
    void setUp() {
        parser = new AmazonEmailParser();
    }

    @Test
    @DisplayName("supports returns true for Amazon sender and subject")
    void supports_returnsTrueForAmazon() {
        assertThat(parser.supports("auto-confirm@amazon.in", "Your Amazon.in order")).isTrue();
        assertThat(parser.supports("order-update@amazon.in", "Ordered: Foot Massager")).isTrue();
        assertThat(parser.supports("orders@swiggy.in", "Swiggy Order")).isFalse();
    }

    @Test
    @DisplayName("parse returns empty when sender or subject is not supported")
    void parse_returnsEmptyWhenNotSupported() {
        assertThat(parser.parse("orders@swiggy.in", "Swiggy Order", "content", LocalDateTime.now())).isEmpty();
    }

    @Test
    @DisplayName("parse extracts Amazon order ID, items, and grand total from legacy table")
    void parse_extractsAmazonReceipt() {
        String sender = "auto-confirm@amazon.in";
        String subject = "Your Amazon.in order of 2 items...";
        LocalDateTime date = LocalDateTime.of(2026, 10, 18, 10, 0);

        String html = """
                <html>
                <body>
                    <p>Order # 402-1234567-8901234</p>
                    <table>
                        <tr>
                            <td>Lifelong LLM99 Foot and Calf Massager with Heat<br>Sold by: Cloudtail India</td>
                            <td>₹ 1,499.00</td>
                        </tr>
                        <tr>
                            <td>Boat Bassheads 100 in-Ear Wired Headphones<br>Sold by: Appario Retail</td>
                            <td>₹ 399.00</td>
                        </tr>
                        <tr>
                            <td>Shipping & Handling</td>
                            <td>₹ 0.00</td>
                        </tr>
                        <tr>
                            <td>Order Total</td>
                            <td>₹ 1,898.00</td>
                        </tr>
                    </table>
                </body>
                </html>
                """;

        Optional<OrderReceipt> opt = parser.parse(sender, subject, html, date);

        assertThat(opt).isPresent();
        OrderReceipt receipt = opt.get();
        assertThat(receipt.platform()).isEqualTo("AMAZON");
        assertThat(receipt.platformOrderId()).isEqualTo("402-1234567-8901234");
        assertThat(receipt.totalAmount()).isEqualTo(1898.0);
        assertThat(receipt.outletName()).isEqualTo("Amazon.in");

        assertThat(receipt.items()).hasSize(2);
        OrderReceiptItem item1 = receipt.items().get(0);
        assertThat(item1.itemName()).contains("Lifelong LLM99 Foot and Calf Massager");
        assertThat(item1.totalPrice()).isEqualTo(1499.0);

        OrderReceiptItem item2 = receipt.items().get(1);
        assertThat(item2.itemName()).contains("Boat Bassheads 100");
        assertThat(item2.totalPrice()).isEqualTo(399.0);
    }

    @Test
    @DisplayName("Single item email parses order ID, item, and total amount")
    void parse_miltonBottleSample() throws Exception {
        try (InputStream is = loadResource("enrichment/amazon/milton_bottle.eml")) {
            MimeMessage msg = new MimeMessage(Session.getDefaultInstance(new Properties()), is);
            String sender = msg.getFrom()[0].toString();
            String subject = msg.getSubject();
            Date sentDate = msg.getSentDate() != null ? msg.getSentDate() : msg.getReceivedDate();
            LocalDateTime dt = sentDate != null
                    ? LocalDateTime.ofInstant(sentDate.toInstant(), ZoneId.systemDefault())
                    : LocalDateTime.now();
            String body = ImapEmailReader.extractBody(msg);

            Optional<OrderReceipt> opt = parser.parse(sender, subject, body, dt);
            assertThat(opt).isPresent();
            OrderReceipt receipt = opt.get();

            assertThat(receipt.platform()).isEqualTo("AMAZON");
            assertThat(receipt.platformOrderId()).isEqualTo("404-6960323-3558726");
            assertThat(receipt.totalAmount()).isEqualTo(1597.0);
            assertThat(receipt.items()).hasSize(1);
            assertThat(receipt.items().get(0).itemName()).contains("MILTON Malmo 1200");
            assertThat(receipt.items().get(0).quantity()).isEqualTo(1);
            assertThat(receipt.items().get(0).totalPrice()).isEqualTo(1592.0);
        }
    }

    @Test
    @DisplayName("Multiple items, single shipment email parses 2 items and order total")
    void parse_esrIpadPencilSample() throws Exception {
        try (InputStream is = loadResource("enrichment/amazon/esr_ipad_pencil.eml")) {
            MimeMessage msg = new MimeMessage(Session.getDefaultInstance(new Properties()), is);
            String sender = msg.getFrom()[0].toString();
            String subject = msg.getSubject();
            Date sentDate = msg.getSentDate() != null ? msg.getSentDate() : msg.getReceivedDate();
            LocalDateTime dt = sentDate != null
                    ? LocalDateTime.ofInstant(sentDate.toInstant(), ZoneId.systemDefault())
                    : LocalDateTime.now();
            String body = ImapEmailReader.extractBody(msg);

            Optional<OrderReceipt> opt = parser.parse(sender, subject, body, dt);
            assertThat(opt).isPresent();
            OrderReceipt receipt = opt.get();

            assertThat(receipt.platform()).isEqualTo("AMAZON");
            assertThat(receipt.platformOrderId()).isEqualTo("404-8090775-2217105");
            assertThat(receipt.totalAmount()).isEqualTo(2880.0);
            assertThat(receipt.items()).hasSize(2);
            assertThat(receipt.items().get(0).itemName()).contains("ESR Geo Digital iPad Pencil");
            assertThat(receipt.items().get(0).totalPrice()).isEqualTo(2326.0);
            assertThat(receipt.items().get(1).itemName()).contains("Robustrion Cover");
            assertThat(receipt.items().get(1).totalPrice()).isEqualTo(549.0);
        }
    }

    @Test
    @DisplayName("Multi item, multi-shipment email aggregates sub-orders into single checkout receipt")
    void parse_jockeyMultiOrderSample() throws Exception {
        try (InputStream is = loadResource("enrichment/amazon/jockey_multi_order.eml")) {
            MimeMessage msg = new MimeMessage(Session.getDefaultInstance(new Properties()), is);
            String sender = msg.getFrom()[0].toString();
            String subject = msg.getSubject();
            Date sentDate = msg.getSentDate() != null ? msg.getSentDate() : msg.getReceivedDate();
            LocalDateTime dt = sentDate != null
                    ? LocalDateTime.ofInstant(sentDate.toInstant(), ZoneId.systemDefault())
                    : LocalDateTime.now();
            String body = ImapEmailReader.extractBody(msg);

            Optional<OrderReceipt> opt = parser.parse(sender, subject, body, dt);
            assertThat(opt).isPresent();
            OrderReceipt receipt = opt.get();

            assertThat(receipt.platform()).isEqualTo("AMAZON");
            assertThat(receipt.platformOrderId()).contains("403-7886314-1369908");
            assertThat(receipt.platformOrderId()).contains("403-9225024-6885112");
            assertThat(receipt.totalAmount()).isEqualTo(723.0);
            assertThat(receipt.items()).hasSize(2);
            assertThat(receipt.items().get(0).itemName()).contains("Jockey FP12");
            assertThat(receipt.items().get(0).totalPrice()).isEqualTo(349.0);
            assertThat(receipt.items().get(1).itemName()).contains("Jockey Men's Cotton Blend");
            assertThat(receipt.items().get(1).totalPrice()).isEqualTo(369.0);
        }
    }

    private InputStream loadResource(String path) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + path);
        }
        return is;
    }
}
