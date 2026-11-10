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

class FlipkartEmailParserTest {

    private FlipkartEmailParser parser;

    @BeforeEach
    void setUp() {
        parser = new FlipkartEmailParser();
    }

    @Test
    @DisplayName("supports returns true for Flipkart sender and subject")
    void supports_returnsTrueForFlipkart() {
        assertThat(parser.supports("orders@flipkart.com", "Order Placed Successfully")).isTrue();
        assertThat(parser.supports("no-reply@flipkart.com", "Your Flipkart Order")).isTrue();
        assertThat(parser.supports("orders@swiggy.in", "Swiggy Order")).isFalse();
    }

    @Test
    @DisplayName("parse extracts Flipkart order details")
    void parse_extractsFlipkartReceipt() {
        String sender = "orders@flipkart.com";
        String subject = "Order Placed Successfully: OD123456789012345678";
        LocalDateTime date = LocalDateTime.of(2026, 10, 20, 16, 45);

        String html = """
                <html>
                <body>
                    <p>Order ID: OD123456789012345678</p>
                    <table>
                        <tr>
                            <td>Wildcraft Casual Backpack 25 L (Blue)<br>Seller: RetailNet</td>
                            <td>₹ 899.00</td>
                        </tr>
                        <tr>
                            <td>Delivery Charges</td>
                            <td>FREE</td>
                        </tr>
                        <tr>
                            <td>Total Amount</td>
                            <td>₹ 899.00</td>
                        </tr>
                    </table>
                </body>
                </html>
                """;

        Optional<OrderReceipt> opt = parser.parse(sender, subject, html, date);

        assertThat(opt).isPresent();
        OrderReceipt receipt = opt.get();
        assertThat(receipt.platform()).isEqualTo("FLIPKART");
        assertThat(receipt.platformOrderId()).isEqualTo("OD123456789012345678");
        assertThat(receipt.totalAmount()).isEqualTo(899.0);
        assertThat(receipt.outletName()).isEqualTo("Flipkart");

        assertThat(receipt.items()).hasSize(1);
        OrderReceiptItem item = receipt.items().get(0);
        assertThat(item.itemName()).contains("Wildcraft Casual Backpack");
        assertThat(item.totalPrice()).isEqualTo(899.0);
    }

    @Test
    @DisplayName("parse returns empty when sender or subject is not supported")
    void parse_returnsEmptyWhenNotSupported() {
        assertThat(parser.parse("orders@swiggy.in", "Swiggy Order", "content", LocalDateTime.now())).isEmpty();
    }

    @Test
    @DisplayName("parse real Flipkart email with multi-item order")
    void parse_realSample1_multiItemOrder() throws Exception {
        try (InputStream is = loadResource("enrichment/flipkart/hawkins_tawa.eml")) {
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
            assertThat(receipt.platform()).isEqualTo("FLIPKART");
            assertThat(receipt.platformOrderId()).isEqualTo("OD438910753848039100");
            assertThat(receipt.totalAmount()).isEqualTo(1439.0);
            assertThat(receipt.outletName()).isEqualTo("Flipkart");
            assertThat(receipt.orderDateTime()).isEqualTo(dt);

            assertThat(receipt.items()).hasSize(2);
            OrderReceiptItem item1 = receipt.items().get(0);
            assertThat(item1.itemName()).contains("Hawkins Triply Stainless Steel Tawa");
            assertThat(item1.quantity()).isEqualTo(1);
            assertThat(item1.unitPrice()).isNull();
            assertThat(item1.totalPrice()).isEqualTo(0.0);

            OrderReceiptItem item2 = receipt.items().get(1);
            assertThat(item2.itemName()).contains("Micvir Back Cover");
            assertThat(item2.quantity()).isEqualTo(1);
            assertThat(item2.unitPrice()).isNull();
            assertThat(item2.totalPrice()).isEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("parse real Flipkart email with multi-item order, exchange discount, and service")
    void parse_realSample2_withExchangeAndTrustShield() throws Exception {
        try (InputStream is = loadResource("enrichment/flipkart/pixel_10a.eml")) {
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
            assertThat(receipt.platform()).isEqualTo("FLIPKART");
            assertThat(receipt.platformOrderId()).isEqualTo("OD438825333246227100");
            assertThat(receipt.totalAmount()).isEqualTo(36334.0);
            assertThat(receipt.outletName()).isEqualTo("Flipkart");
            assertThat(receipt.orderDateTime()).isEqualTo(dt);

            assertThat(receipt.items()).hasSize(2);
            OrderReceiptItem item1 = receipt.items().get(0);
            assertThat(item1.itemName()).contains("Flipkart Trust Shield");
            assertThat(item1.quantity()).isEqualTo(1);
            assertThat(item1.unitPrice()).isNull();

            OrderReceiptItem item2 = receipt.items().get(1);
            assertThat(item2.itemName()).contains("Google Pixel 10a");
            assertThat(item2.quantity()).isEqualTo(1);
            assertThat(item2.unitPrice()).isNull();
        }
    }

    @Test
    @DisplayName("parse real Flipkart email with single-item order attributing total to item")
    void parse_realSample3_singleItemOrder() throws Exception {
        try (InputStream is = loadResource("enrichment/flipkart/realme_buds.eml")) {
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
            assertThat(receipt.platform()).isEqualTo("FLIPKART");
            assertThat(receipt.platformOrderId()).isEqualTo("OD438841712844663100");
            assertThat(receipt.totalAmount()).isEqualTo(1718.0);
            assertThat(receipt.outletName()).isEqualTo("Flipkart");
            assertThat(receipt.orderDateTime()).isEqualTo(dt);

            assertThat(receipt.items()).hasSize(1);
            OrderReceiptItem item = receipt.items().get(0);
            assertThat(item.itemName()).contains("realme Buds T310");
            assertThat(item.quantity()).isEqualTo(1);
            assertThat(item.unitPrice()).isEqualTo(1718.0);
            assertThat(item.totalPrice()).isEqualTo(1718.0);
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
