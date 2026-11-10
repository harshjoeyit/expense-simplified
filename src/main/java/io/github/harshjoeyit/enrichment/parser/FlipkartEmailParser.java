package io.github.harshjoeyit.enrichment.parser;

import io.github.harshjoeyit.enrichment.model.OrderReceipt;
import io.github.harshjoeyit.enrichment.model.OrderReceiptItem;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FlipkartEmailParser implements EmailReceiptParser {

    public static final String DEFAULT_OUTLET_NAME = "Flipkart";

    private static final Pattern FLIPKART_ORDER_ID_PATTERN = Pattern.compile(
            "\\b(OD[0-9]{10,20})\\b"
    );

    private static final Pattern TOTAL_AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:total\\s+amount|amount\\s+paid|order\\s+total|grand\\s+total).*?(?:[₹₨]\\.?|Rs\\.?|INR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"
    );

    private static final Pattern QTY_PATTERN = Pattern.compile("(?i)(?:quantity|qty)[:\\s]*([0-9]+)");

    @Override
    public String platform() {
        return "FLIPKART";
    }

    @Override
    public boolean supports(String sender, String subject) {
        String s = sender != null ? sender.toLowerCase() : "";
        String sub = subject != null ? subject.toLowerCase() : "";
        return s.contains("flipkart.com") || sub.contains("flipkart");
    }

    @Override
    public Optional<OrderReceipt> parse(String sender, String subject, String bodyContent, LocalDateTime emailDate) {
        if (!supports(sender, subject) || bodyContent == null || bodyContent.isBlank()) {
            return Optional.empty();
        }

        Document doc = Jsoup.parse(bodyContent);
        String bodyText = doc.text();

        // Order ID
        String orderId = EmailParserUtils.matchFirst(subject, FLIPKART_ORDER_ID_PATTERN, 1)
                .or(() -> EmailParserUtils.matchFirst(bodyText, FLIPKART_ORDER_ID_PATTERN, 1))
                .orElse(null);

        // Extract Total Amount
        Double total = extractTotal(doc, bodyText);
        if (total == null || total <= 0) {
            log.debug("Flipkart email skipped: total amount not found in subject '{}'", subject);
            return Optional.empty();
        }

        // Extract Items
        List<OrderReceiptItem> items = extractItems(doc, total);

        return Optional.of(OrderReceipt.builder()
                .platform(platform())
                .platformOrderId(orderId)
                .outletName(DEFAULT_OUTLET_NAME)
                .orderDateTime(emailDate != null ? emailDate : LocalDateTime.now())
                .totalAmount(total)
                .items(items)
                .build());
    }

    private List<OrderReceiptItem> extractItems(Document doc, Double totalAmount) {
        List<OrderReceiptItem> items = new ArrayList<>();

        // Strategy 1: Real Flipkart transactional email layout
        // Leaf <td> cells that contain "Seller:" or "Qty:"
        for (Element td : doc.select("td")) {
            if (!td.select("table").isEmpty()) {
                // Outer container with nested tables; skip
                continue;
            }

            String tdText = td.text().trim();
            if (isSummaryOrNoise(tdText)) {
                continue;
            }

            boolean hasQty = Pattern.compile("(?i)(?:quantity|qty)[:\\s]*[0-9]+").matcher(tdText).find();
            boolean hasSeller = Pattern.compile("(?i)seller[:\\s]").matcher(tdText).find();

            if (hasQty || hasSeller) {
                // Extract product name: prefer <a> link text, else text before seller/delivery/qty
                String itemName = null;
                for (Element a : td.select("a")) {
                    String aText = a.text().trim();
                    if (aText.length() > 3 && !isSummaryOrNoise(aText)) {
                        itemName = aText;
                        break;
                    }
                }

                if (itemName == null || itemName.isBlank()) {
                    String[] parts = tdText.split("(?i)(?:delivery|seller[:\\s]|qty[:\\s]|quantity[:\\s])");
                    if (parts.length > 0 && parts[0].trim().length() > 3 && !isSummaryOrNoise(parts[0].trim())) {
                        itemName = parts[0].trim();
                    }
                }

                if (itemName != null && !itemName.isBlank() && !isSummaryOrNoise(itemName)) {
                    int qty = 1;
                    Matcher qm = QTY_PATTERN.matcher(tdText);
                    if (qm.find()) {
                        qty = EmailParserUtils.parseQuantity(qm.group(1));
                    }
                    final int finalQty = qty;

                    // Check if price is present in this cell or row
                    Optional<Double> priceOpt = EmailParserUtils.parsePrice(tdText);
                    Double unitPrice = priceOpt.map(p -> p / finalQty).orElse(null);
                    double totalPrice = priceOpt.orElse(0.0);

                    items.add(OrderReceiptItem.builder()
                            .itemName(itemName)
                            .quantity(finalQty)
                            .unitPrice(unitPrice)
                            .totalPrice(totalPrice)
                            .build());
                }
            }
        }

        // Fallback to standard 2-column tabular rows (e.g. synthetic or invoice tables)
        if (items.isEmpty()) {
            for (Element row : doc.select("tr")) {
                Elements cols = row.select("td");
                if (cols.size() >= 2) {
                    String col0 = cols.get(0).text().trim();
                    String colLast = cols.last().text().trim();

                    if (isSummaryOrNoise(col0)) {
                        continue;
                    }

                    Optional<Double> priceOpt = EmailParserUtils.parsePrice(colLast);
                    if (priceOpt.isPresent() && priceOpt.get() > 0 && col0.length() > 3) {
                        int qty = 1;
                        Matcher qm = QTY_PATTERN.matcher(col0);
                        if (qm.find()) {
                            qty = EmailParserUtils.parseQuantity(qm.group(1));
                        }

                        String cleanName = col0.split("(?i)(seller:|seller\\s*name|quantity:|qty:)")[0].trim();
                        if (!cleanName.isBlank() && !isSummaryOrNoise(cleanName)) {
                            items.add(OrderReceiptItem.builder()
                                    .itemName(cleanName)
                                    .quantity(qty)
                                    .unitPrice(priceOpt.get() / qty)
                                    .totalPrice(priceOpt.get())
                                    .build());
                        }
                    }
                }
            }
        }

        // If only 1 item was found and its individual price was not specified in the email,
        // attribute the known total amount to this single item.
        if (items.size() == 1 && items.get(0).totalPrice() <= 0 && totalAmount != null && totalAmount > 0) {
            OrderReceiptItem single = items.get(0);
            return List.of(OrderReceiptItem.builder()
                    .itemName(single.itemName())
                    .quantity(single.quantity())
                    .unitPrice(single.quantity() > 0 ? totalAmount / single.quantity() : totalAmount)
                    .totalPrice(totalAmount)
                    .build());
        }

        return items;
    }

    private Double extractTotal(Document doc, String bodyText) {
        for (Element row : doc.select("tr")) {
            String rowText = row.text().toLowerCase();
            if (rowText.contains("total amount") || rowText.contains("amount paid") || rowText.contains("order total")) {
                Elements cols = row.select("td, th");
                if (!cols.isEmpty()) {
                    Optional<Double> p = EmailParserUtils.parsePrice(cols.last().text());
                    if (p.isPresent() && p.get() > 0) {
                        return p.get();
                    }
                }
            }
        }

        Matcher m = TOTAL_AMOUNT_PATTERN.matcher(bodyText);
        if (m.find()) {
            Optional<Double> p = EmailParserUtils.parsePrice(m.group(1));
            if (p.isPresent()) {
                return p.get();
            }
        }

        return null;
    }

    private boolean isSummaryOrNoise(String text) {
        String lower = text.toLowerCase();
        return lower.contains("total amount")
                || lower.contains("amount paid")
                || lower.contains("order total")
                || lower.contains("price details")
                || lower.contains("delivery charges")
                || lower.contains("delivery address")
                || lower.contains("delivery fee")
                || lower.contains("convenience fee")
                || lower.contains("manage your order")
                || lower.contains("customer care")
                || lower.contains("download invoice")
                || lower.contains("product exchange value")
                || lower.contains("pick up charges")
                || lower.contains("payment option")
                || lower.equals("here")
                || lower.equals("click here");
    }
}
