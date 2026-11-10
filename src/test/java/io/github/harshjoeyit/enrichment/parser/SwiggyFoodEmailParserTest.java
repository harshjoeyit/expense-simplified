package io.github.harshjoeyit.enrichment.parser;

import io.github.harshjoeyit.enrichment.model.OrderReceipt;
import io.github.harshjoeyit.enrichment.model.OrderReceiptItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SwiggyFoodEmailParserTest {

    private SwiggyFoodEmailParser parser;

    @BeforeEach
    void setUp() {
        parser = new SwiggyFoodEmailParser();
    }

    @Test
    @DisplayName("supports returns true for Swiggy Food sender/subject, false for Instamart and others")
    void supports_returnsTrueOnlyForSwiggyFood() {
        assertThat(parser.supports("orders@swiggy.in", "Your order has been delivered")).isTrue();
        assertThat(parser.supports("no-reply@swiggy.in", "Order Confirmed")).isTrue();
        assertThat(parser.supports("random@gmail.com", "Swiggy Order Receipt")).isTrue();
        assertThat(parser.supports("noreply@instamart.in", "Your Instamart order was successfully delivered")).isFalse();
        assertThat(parser.supports("random@gmail.com", "Your Instamart order")).isFalse();
        assertThat(parser.supports("auto-confirm@amazon.in", "Your Amazon Order")).isFalse();
    }

    @Test
    @DisplayName("parse extracts restaurant name, items, order ID, and total amount from HTML table")
    void parse_extractsReceiptDetails() {
        String sender = "orders@swiggy.in";
        String subject = "Delivered: Your order from Truffles has been delivered";
        LocalDateTime now = LocalDateTime.of(2026, 10, 15, 13, 30);

        String html = """
                <html>
                <body>
                    <h2>Order #18392104</h2>
                    <p>Thank you for ordering with Truffles!</p>
                    <table>
                        <tr>
                            <td>1 x All American Burger</td>
                            <td>₹ 210.00</td>
                        </tr>
                        <tr>
                            <td>1 x Peri Peri Fries</td>
                            <td>₹ 80.00</td>
                        </tr>
                        <tr>
                            <td>Item Total</td>
                            <td>₹ 290.00</td>
                        </tr>
                        <tr>
                            <td>Delivery Fee</td>
                            <td>₹ 30.00</td>
                        </tr>
                        <tr>
                            <td>Total Paid</td>
                            <td>₹ 320.00</td>
                        </tr>
                    </table>
                </body>
                </html>
                """;

        Optional<OrderReceipt> optReceipt = parser.parse(sender, subject, html, now);

        assertThat(optReceipt).isPresent();
        OrderReceipt receipt = optReceipt.get();
        assertThat(receipt.platform()).isEqualTo("SWIGGY");
        assertThat(receipt.outletName()).isEqualTo("Truffles");
        assertThat(receipt.platformOrderId()).isEqualTo("18392104");
        assertThat(receipt.totalAmount()).isEqualTo(320.0);

        assertThat(receipt.items()).hasSize(2);
        OrderReceiptItem item1 = receipt.items().get(0);
        assertThat(item1.itemName()).isEqualTo("All American Burger");
        assertThat(item1.quantity()).isEqualTo(1);
        assertThat(item1.totalPrice()).isEqualTo(210.0);

        OrderReceiptItem item2 = receipt.items().get(1);
        assertThat(item2.itemName()).isEqualTo("Peri Peri Fries");
        assertThat(item2.quantity()).isEqualTo(1);
        assertThat(item2.totalPrice()).isEqualTo(80.0);
    }


    @Test
    @DisplayName("Sample 2: Swiggy Food sample parses restaurant from Order Journey, item with x1 suffix, and paid via card amount")
    void parse_swiggyFoodSample() {
        String sender = "Swiggy <noreply@swiggy.in>";
        String subject = "Your Swiggy order was delivered superfast";
        String body = """
                ORDER JOURNEY
                Faasos - Wraps, Rolls & Shawarma
                FRMS Landmark Apartment,No 195/4-1/G-1, Ground Floor, Kaggadaspura CV …
                Sep 20, 8:21 PM
                Harshit
                GF-1, Byrasandra, C V Raman Nagar, Bengaluru, Karnataka, India, Bangalore
                Sep 20, 8:52 PM
                Order ID: 248885519128559
                BILL DETAILS
                Masala Paneer Tikka Wrap x1 ₹199
                Restaurant Packaging ₹20.92
                Platform fee with GST ₹17.98
                Discount Applied (TREATS) - ₹139.30
                Delivery Fee (FREE with Swiggy One) | 1.6 kms FREE
                Expiry Cash Discount - ₹40
                Taxes ₹7.41
                Paid Via Credit/Debit card ₹66
                """;

        assertThat(parser.supports(sender, subject)).isTrue();
        Optional<OrderReceipt> optReceipt = parser.parse(sender, subject, body, LocalDateTime.now());
        assertThat(optReceipt).isPresent();
        OrderReceipt receipt = optReceipt.get();

        assertThat(receipt.platform()).isEqualTo("SWIGGY");
        assertThat(receipt.outletName()).isEqualTo("Faasos - Wraps, Rolls & Shawarma");
        assertThat(receipt.platformOrderId()).isEqualTo("248885519128559");
        // Total MUST be 66.0 (the actual amount paid via credit card) so it matches the bank txn
        assertThat(receipt.totalAmount()).isEqualTo(66.0);

        assertThat(receipt.items()).hasSize(1);
        assertThat(receipt.items().get(0).itemName()).isEqualTo("Masala Paneer Tikka Wrap");
        assertThat(receipt.items().get(0).quantity()).isEqualTo(1);
        assertThat(receipt.items().get(0).totalPrice()).isEqualTo(199.0);
    }

    @Test
    @DisplayName("Sample 3: Swiggy Food Meghana Foods (Paid via Swiggy UPI)")
    void parse_swiggyFoodSample3() {
        String sender = "Swiggy <noreply@swiggy.in>";
        String subject = "Your Swiggy order was successfully delivered";
        String body = """
                ₹50 saved on this order
                ₹50 saved with Swiggy
                ORDER JOURNEY
                Meghana Foods
                SY No.18/2A, 1ST FLOOR G R S TOWERS SARJAPURA ROAD,BELLANDU…
                May 31, 11:54 PM
                Harshit
                205, Owners Court Layout Rd, Kasavanahalli, Bengaluru, Karnataka 560035, In…
                Jun 1, 12:40 AM
                Order ID: 239221496373976
                BILL DETAILS
                Spl Veg Biryani x1 ₹340
                Paneer Biryani x1 ₹380
                Restaurant Packaging ₹30.00
                Platform fee with GST ₹17.58
                Delivery Fee (FREE with Swiggy One) ₹50 FREE
                Taxes ₹36.00
                Paid Via Swiggy UPI ₹804.00
                """;

        Optional<OrderReceipt> optReceipt = parser.parse(sender, subject, body, LocalDateTime.now());
        assertThat(optReceipt).isPresent();
        OrderReceipt receipt = optReceipt.get();

        assertThat(receipt.outletName()).isEqualTo("Meghana Foods");
        assertThat(receipt.platformOrderId()).isEqualTo("239221496373976");
        assertThat(receipt.totalAmount()).isEqualTo(804.0);
        assertThat(receipt.items()).hasSize(2);
        assertThat(receipt.items().get(0).itemName()).isEqualTo("Spl Veg Biryani");
        assertThat(receipt.items().get(0).totalPrice()).isEqualTo(340.0);
        assertThat(receipt.items().get(1).itemName()).isEqualTo("Paneer Biryani");
        assertThat(receipt.items().get(1).totalPrice()).isEqualTo(380.0);
    }

    @Test
    @DisplayName("Sample 4: Swiggy Food Anand Sweets (Order No, Item Name Quantity Price table)")
    void parse_swiggyFoodSample4() {
        String sender = "Swiggy <noreply@swiggy.in>";
        String subject = "Your Swiggy order was successfully delivered";
        String body = """
                Greetings from Swiggy
                Your order was delivered successfully. Rate your delivery experience here
                Order No:
                226572472245194
                Restaurant
                Anand Sweets & Savouries
                Your Order Summary:
                Order No: 226572472245194
                Order placed at: Monday, January 5, 2026 2:17 PM
                Order delivered at: Monday, January 5, 2026 3:19 PM
                Order Status: Delivered
                Ordered from:
                Anand Sweets & Savouries
                Shop No.7, Anjaneya complex, Airport Road, Kodihalli, Banglore - 560 008.
                Delivery To:
                Harshit
                GF-1
                XMJ8+RX4, Byrasandra, C V Raman Nagar,
                Bengaluru, Karnataka 560093, India (Helios
                Business Park) (Pragathi Onyx)
                Bengaluru
                Item Name Quantity Price
                Kaju Mysore Pak 1 ₹ 265
                Mysore Pak 1 ₹ 240
                Item Total: ₹ 505.00
                Restaurant Packaging: ₹ 10.00
                Platform Fee: ₹ 14.99
                Discount Applied (VISABOBDC): - ₹ 101.00
                Delivery Fee (FREE with Swiggy One): FREE
                Taxes: ₹ 25.75
                Paid Via Credit/Debit card: ₹ 455.00
                Order Total: ₹ 455
                """;

        Optional<OrderReceipt> optReceipt = parser.parse(sender, subject, body, LocalDateTime.now());
        assertThat(optReceipt).isPresent();
        OrderReceipt receipt = optReceipt.get();

        assertThat(receipt.outletName()).isEqualTo("Anand Sweets & Savouries");
        assertThat(receipt.platformOrderId()).isEqualTo("226572472245194");
        assertThat(receipt.totalAmount()).isEqualTo(455.0);
        assertThat(receipt.items()).hasSize(2);
        assertThat(receipt.items().get(0).itemName()).isEqualTo("Kaju Mysore Pak");
        assertThat(receipt.items().get(0).quantity()).isEqualTo(1);
        assertThat(receipt.items().get(0).totalPrice()).isEqualTo(265.0);
        assertThat(receipt.items().get(1).itemName()).isEqualTo("Mysore Pak");
        assertThat(receipt.items().get(1).quantity()).isEqualTo(1);
        assertThat(receipt.items().get(1).totalPrice()).isEqualTo(240.0);
    }

    @Test
    @DisplayName("parse returns empty when sender or subject is not supported")
    void parse_returnsEmptyWhenNotSupported() {
        assertThat(parser.parse("noreply@instamart.in", "Instamart Order", "body content", LocalDateTime.now())).isEmpty();
        assertThat(parser.parse("auto-confirm@amazon.in", "Amazon Order", "body content", LocalDateTime.now())).isEmpty();
    }
}
