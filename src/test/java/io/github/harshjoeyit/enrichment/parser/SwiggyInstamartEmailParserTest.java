package io.github.harshjoeyit.enrichment.parser;

import io.github.harshjoeyit.enrichment.model.OrderReceipt;
import io.github.harshjoeyit.enrichment.model.OrderReceiptItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SwiggyInstamartEmailParserTest {

    private SwiggyInstamartEmailParser parser;

    @BeforeEach
    void setUp() {
        parser = new SwiggyInstamartEmailParser();
    }

    @Test
    @DisplayName("supports returns true for Instamart sender and subject, false for others")
    void supports_returnsTrueOnlyForInstamart() {
        assertThat(parser.supports("noreply@instamart.in", "Your Instamart order was successfully delivered")).isTrue();
        assertThat(parser.supports("orders@instamart.in", "Order Confirmed")).isTrue();
        assertThat(parser.supports("user@gmail.com", "Instamart order update")).isTrue();
        assertThat(parser.supports("orders@swiggy.in", "Your order has been delivered")).isFalse();
        assertThat(parser.supports("auto-confirm@amazon.in", "Your Amazon Order")).isFalse();
        assertThat(parser.supports(null, null)).isFalse();
    }

    @Test
    @DisplayName("parse extracts Instamart order details from plain text sample")
    void parse_instamartPlainText() {
        String sender = "noreply@instamart.in <noreply@instamart.in>";
        String subject = "Your Instamart order was successfully delivered";
        String body = """
                Greetings from Instamart👋
                Your Instamart order id: 253163262996179 was successfully delivered.
                Deliver To:
                Corner House - 3 Manzil (White) Near Khali Plot, Infront of XYZ Enterprises Gandhi Puram,
                Izatnagar, Bareilly, Uttar Pradesh 243502, India
                Order Items
                1 x Metro Living Plastic Dustbin with Swing
                Lid (7L, Brown) ₹281.00
                1 x URGO Medium Lemon-Scented
                Garbage Bags / 19 x 21in (48 x 53cm) ₹75.00
                Order Summary
                Item Bill ₹356.00
                Handling Fee ₹12.00
                Delivery Partner Fee ₹0.00
                Discount - ₹8.99
                Grand Total ₹359.00
                Get the
                App:
                Follow
                us:
                """;

        Optional<OrderReceipt> optReceipt = parser.parse(sender, subject, body, LocalDateTime.of(2026, 11, 9, 8, 50));
        assertThat(optReceipt).isPresent();
        OrderReceipt receipt = optReceipt.get();

        assertThat(receipt.platform()).isEqualTo("SWIGGY");
        assertThat(receipt.outletName()).isEqualTo("Instamart");
        assertThat(receipt.platformOrderId()).isEqualTo("253163262996179");
        assertThat(receipt.totalAmount()).isEqualTo(359.0);

        assertThat(receipt.items()).hasSize(2);
        OrderReceiptItem item1 = receipt.items().get(0);
        assertThat(item1.itemName()).contains("Metro Living Plastic Dustbin");
        assertThat(item1.quantity()).isEqualTo(1);
        assertThat(item1.totalPrice()).isEqualTo(281.0);

        OrderReceiptItem item2 = receipt.items().get(1);
        assertThat(item2.itemName()).contains("URGO Medium Lemon-Scented");
        assertThat(item2.quantity()).isEqualTo(1);
        assertThat(item2.totalPrice()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("parse extracts Instamart order details from HTML email")
    void parse_instamartHtml() {
        String sender = "noreply@instamart.in";
        String subject = "Your Instamart order was successfully delivered";
        String html = """
                <html>
                <body>
                    <p>Greetings from Instamart👋</p>
                    <p>Your Instamart order id: 253163262996179 was successfully delivered.</p>
                    <table border='0' width='100%' cellspacing='10' cellpadding='0'>
                        <tbody>
                            <tr>
                                <td width='50%' style='font-size: 15px'>1 x Metro Living Plastic Dustbin with Swing Lid (7L, Brown)</td>
                                <td width='50%' style='text-align: right;'><span style='font-size: 15px'>₹281.00</span></td>
                            </tr>
                            <tr>
                                <td width='50%' style='font-size: 15px'>1 x URGO Medium Lemon-Scented Garbage Bags / 19 x 21in (48 x 53cm)</td>
                                <td width='50%' style='text-align: right;'><span style='font-size: 15px'>₹75.00</span></td>
                            </tr>
                        </tbody>
                    </table>
                    <table border='0' width='100%' cellspacing='10' cellpadding='0'>
                        <tbody>
                            <tr>
                                <td width='50%'>Item Bill</td>
                                <td width='50%' style='text-align: right;'><span>₹356.00</span></td>
                            </tr>
                            <tr>
                                <td width='50%'>Handling Fee</td>
                                <td width='50%' style='text-align: right;'><span>₹12.00</span></td>
                            </tr>
                            <tr>
                                <td width='50%'>Grand Total</td>
                                <td width='50%' style='text-align: right;'><span>₹359.00</span></td>
                            </tr>
                        </tbody>
                    </table>
                </body>
                </html>
                """;

        Optional<OrderReceipt> optReceipt = parser.parse(sender, subject, html, LocalDateTime.of(2026, 11, 9, 8, 50));
        assertThat(optReceipt).isPresent();
        OrderReceipt receipt = optReceipt.get();

        assertThat(receipt.platform()).isEqualTo("SWIGGY");
        assertThat(receipt.outletName()).isEqualTo("Instamart");
        assertThat(receipt.platformOrderId()).isEqualTo("253163262996179");
        assertThat(receipt.totalAmount()).isEqualTo(359.0);

        assertThat(receipt.items()).hasSize(2);
        assertThat(receipt.items().get(0).itemName()).isEqualTo("Metro Living Plastic Dustbin with Swing Lid (7L, Brown)");
        assertThat(receipt.items().get(0).quantity()).isEqualTo(1);
        assertThat(receipt.items().get(0).totalPrice()).isEqualTo(281.0);

        assertThat(receipt.items().get(1).itemName()).isEqualTo("URGO Medium Lemon-Scented Garbage Bags / 19 x 21in (48 x 53cm)");
        assertThat(receipt.items().get(1).quantity()).isEqualTo(1);
        assertThat(receipt.items().get(1).totalPrice()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("parse returns empty optional on blank content, unsupported sender, or missing total")
    void parse_returnsEmptyOnInvalidContent() {
        assertThat(parser.parse("noreply@instamart.in", "Subject", "", LocalDateTime.now())).isEmpty();
        assertThat(parser.parse("noreply@instamart.in", "Subject", "   ", LocalDateTime.now())).isEmpty();
        assertThat(parser.parse("noreply@instamart.in", "Subject", null, LocalDateTime.now())).isEmpty();
        assertThat(parser.parse("orders@swiggy.in", "Swiggy Food", "content", LocalDateTime.now())).isEmpty();
    }
}
