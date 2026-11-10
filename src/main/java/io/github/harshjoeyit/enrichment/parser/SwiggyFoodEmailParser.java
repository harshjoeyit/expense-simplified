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
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SwiggyFoodEmailParser implements EmailReceiptParser {

    public static final String DEFAULT_OUTLET_NAME = "Swiggy";

    private static final Pattern OUTLET_FROM_SUBJECT = Pattern.compile(
            "(?i)(?:order from|order with|at)\\s+([A-Za-z0-9&'’\\s\\-\\.]+?)(?:\\s+(?:has been|was|is|delivered|confirmed|-|!)|$)"
    );

    private static final Pattern OUTLET_FROM_JOURNEY = Pattern.compile(
            "(?i)ORDER\\s+JOURNEY\\s*\\n+([^\n\r]+)"
    );

    private static final Pattern OUTLET_FROM_HEADER = Pattern.compile(
            "(?i)(?:Restaurant|Ordered\\s+from:?)\\s*\\n+([^\n\r]+)"
    );

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile(
            "(?i)(?:order\\s*(?:#|id|no\\.?|number)[:\\s]*)\\s*([0-9A-Za-z]+)"
    );

    private static final Pattern QTY_PREFIX_PATTERN = Pattern.compile(
            "^([0-9]+)\\s*[xX*]\\s*(.*)$"
    );

    private static final Pattern QTY_SUFFIX_PATTERN = Pattern.compile(
            "^(.*?)\\s*[xX*]\\s*([0-9]+)$"
    );

    private static final Pattern QTY_PLAIN_SUFFIX_PATTERN = Pattern.compile(
            "^(.*?)\\s+([0-9]{1,3})$"
    );

    private static final Pattern PAID_VIA_PATTERN = Pattern.compile(
            "(?i)paid\\s+via\\s+.*?(?:₹|Rs\\.?|INR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"
    );

    private static final Pattern TOTAL_PAID_PATTERN = Pattern.compile(
            "(?i)(?:total\\s+paid|grand\\s+total|order\\s+total|bill\\s+total|amount\\s+paid).*?(?:₹|Rs\\.?|INR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"
    );

    @Override
    public String platform() {
        return "SWIGGY";
    }

    @Override
    public boolean supports(String sender, String subject) {
        String s = sender != null ? sender.toLowerCase(Locale.ROOT) : "";
        String sub = subject != null ? subject.toLowerCase(Locale.ROOT) : "";
        return (s.contains("swiggy.in") || sub.contains("swiggy"))
                && !s.contains("instamart") && !sub.contains("instamart");
    }

    @Override
    public Optional<OrderReceipt> parse(String sender, String subject, String bodyContent, LocalDateTime emailDate) {
        if (!supports(sender, subject) || bodyContent == null || bodyContent.isBlank()) {
            return Optional.empty();
        }

        Document bodyHtml = Jsoup.parse(bodyContent);
        String bodyText = bodyHtml.text();

        // Outlet Name (Restaurant)
        String outletName = EmailParserUtils.matchFirst(subject, OUTLET_FROM_SUBJECT, 1)
                .orElse(null);

        if (outletName == null) {
            outletName = EmailParserUtils.matchFirst(bodyContent, OUTLET_FROM_JOURNEY, 1)
                    .orElse(null);
        }

        if (outletName == null) {
            outletName = EmailParserUtils.matchFirst(bodyContent, OUTLET_FROM_HEADER, 1)
                    .orElse(null);
        }

        if (outletName == null) {
            // Attempt extraction from HTML headings or bold tags
            for (Element el : bodyHtml.select("h1, h2, h3, b, strong, td")) {
                String text = el.text().trim();
                Matcher m = OUTLET_FROM_SUBJECT.matcher(text);
                if (m.find()) {
                    outletName = m.group(1).trim();
                    break;
                }
            }
        }
        if (outletName == null) {
            outletName = DEFAULT_OUTLET_NAME;
        }

        // Order ID
        String orderId = EmailParserUtils.matchFirst(subject, ORDER_ID_PATTERN, 1)
                .or(() -> EmailParserUtils.matchFirst(bodyContent, ORDER_ID_PATTERN, 1))
                .or(() -> EmailParserUtils.matchFirst(bodyText, ORDER_ID_PATTERN, 1))
                .orElse(null);

        // Extract Items (HTML table rows first, fallback to text/plain parsing)
        List<OrderReceiptItem> items = extractItemsFromTable(bodyHtml);
        if (items.isEmpty()) {
            items = extractItemsFromText(bodyContent);
        }

        // Extract Total Amount
        Double total = extractTotal(bodyHtml, bodyContent, bodyText, items);
        if (total == null || total <= 0) {
            log.debug("Swiggy email skipped: total amount not found in subject '{}'", subject);
            return Optional.empty();
        }

        return Optional.of(OrderReceipt.builder()
                .platform(platform())
                .platformOrderId(orderId)
                .outletName(outletName)
                .orderDateTime(emailDate != null ? emailDate : LocalDateTime.now())
                .totalAmount(total)
                .items(items)
                .build());
    }

    private List<OrderReceiptItem> extractItemsFromTable(Document doc) {
        List<OrderReceiptItem> items = new ArrayList<>();

        for (Element row : doc.select("tr")) {
            Elements cols = row.select("td, th");
            if (cols.size() >= 2) {
                String col0 = cols.get(0).text().trim();
                String colLast = cols.get(cols.size() - 1).text().trim();

                if (isSummaryOrHeader(col0)) {
                    continue;
                }

                Optional<Double> priceOpt = EmailParserUtils.parsePrice(colLast);
                if (priceOpt.isPresent() && priceOpt.get() > 0 && !col0.isBlank()) {
                    int qty = 1;
                    String itemName = col0;

                    Matcher qm = QTY_PREFIX_PATTERN.matcher(col0);
                    if (qm.matches()) {
                        qty = EmailParserUtils.parseQuantity(qm.group(1));
                        itemName = qm.group(2).trim();
                    } else {
                        Matcher sm = QTY_SUFFIX_PATTERN.matcher(col0);
                        if (sm.matches()) {
                            itemName = sm.group(1).trim();
                            qty = EmailParserUtils.parseQuantity(sm.group(2));
                        } else if (cols.size() >= 3) {
                            String midCol = cols.get(1).text().trim();
                            qty = EmailParserUtils.parseQuantity(midCol);
                        }
                    }

                    if (!isSummaryOrHeader(itemName)) {
                        items.add(OrderReceiptItem.builder()
                                .itemName(itemName)
                                .quantity(qty)
                                .unitPrice(priceOpt.get() / qty)
                                .totalPrice(priceOpt.get())
                                .build());
                    }
                }
            }
        }

        return items;
    }

    private List<OrderReceiptItem> extractItemsFromText(String text) {
        List<OrderReceiptItem> items = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return items;
        }

        String section = text;
        Matcher startMatcher = Pattern.compile("(?i)(?:Order Items|BILL DETAILS|Item Name\\s+Quantity\\s+Price)").matcher(text);
        if (startMatcher.find()) {
            section = text.substring(startMatcher.end());
        }

        Matcher endMatcher = Pattern.compile("(?i)(?:Order Summary|Restaurant Packaging|Platform fee|Delivery Partner Fee|Paid Via|Grand Total|Item Bill|Item Total)").matcher(section);
        if (endMatcher.find()) {
            section = section.substring(0, endMatcher.start());
        }

        String[] rawLines = section.split("\\r?\\n");
        StringBuilder currentItemText = new StringBuilder();

        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            if (line.isBlank() || isSummaryOrHeader(line) || line.startsWith("http") || line.contains("Gmail -")) {
                continue;
            }

            Optional<Double> priceOpt = EmailParserUtils.parsePrice(line);
            if (priceOpt.isPresent()) {
                currentItemText.append(" ").append(line);
                String fullItemLine = currentItemText.toString().trim();
                currentItemText.setLength(0);

                String withoutPrice = fullItemLine.replaceAll("(?:₹|Rs\\.?|INR)?\\s*[0-9,]+(?:\\.[0-9]{1,2})?\\s*$", "").trim();

                int qty = 1;
                String itemName = withoutPrice;

                Matcher qm = QTY_PREFIX_PATTERN.matcher(withoutPrice);
                if (qm.matches()) {
                    qty = EmailParserUtils.parseQuantity(qm.group(1));
                    itemName = qm.group(2).trim();
                } else {
                    Matcher sm = QTY_SUFFIX_PATTERN.matcher(withoutPrice);
                    if (sm.matches()) {
                        itemName = sm.group(1).trim();
                        qty = EmailParserUtils.parseQuantity(sm.group(2));
                    } else {
                        Matcher psm = QTY_PLAIN_SUFFIX_PATTERN.matcher(withoutPrice);
                        if (psm.matches()) {
                            itemName = psm.group(1).trim();
                            qty = EmailParserUtils.parseQuantity(psm.group(2));
                        }
                    }
                }

                if (!itemName.isBlank() && !isSummaryOrHeader(itemName)) {
                    items.add(OrderReceiptItem.builder()
                            .itemName(itemName)
                            .quantity(qty)
                            .unitPrice(priceOpt.get() / qty)
                            .totalPrice(priceOpt.get())
                            .build());
                }
            } else {
                if (!currentItemText.isEmpty()) {
                    currentItemText.append(" ");
                }
                currentItemText.append(line);
            }
        }

        return items;
    }

    private Double extractTotal(Document doc, String rawContent, String bodyText, List<OrderReceiptItem> items) {
        // Primary: "Paid Via ..." (Credit/Debit card, UPI, etc.) is the actual charged amount
        Matcher paidViaMatcher = PAID_VIA_PATTERN.matcher(rawContent);
        if (paidViaMatcher.find()) {
            Optional<Double> p = EmailParserUtils.parsePrice(paidViaMatcher.group(1));
            if (p.isPresent() && p.get() > 0) {
                return p.get();
            }
        }

        // Fallback 1: Grand Total / Total Paid from table rows
        for (Element row : doc.select("tr")) {
            String rowText = row.text();
            if (isTotalRow(rowText)) {
                Elements cols = row.select("td, th");
                if (!cols.isEmpty()) {
                    String lastText = cols.last().text();
                    Optional<Double> p = EmailParserUtils.parsePrice(lastText);
                    if (p.isPresent() && p.get() > 0) {
                        return p.get();
                    }
                }
            }
        }

        // Fallback 2: Regex for Grand Total / Total Paid on text content
        Matcher totalMatcher = TOTAL_PAID_PATTERN.matcher(rawContent);
        if (totalMatcher.find()) {
            Optional<Double> p = EmailParserUtils.parsePrice(totalMatcher.group(1));
            if (p.isPresent() && p.get() > 0) {
                return p.get();
            }
        }

        Matcher totalMatcherBody = TOTAL_PAID_PATTERN.matcher(bodyText);
        if (totalMatcherBody.find()) {
            Optional<Double> p = EmailParserUtils.parsePrice(totalMatcherBody.group(1));
            if (p.isPresent() && p.get() > 0) {
                return p.get();
            }
        }

        // Final Fallback: sum of items
        if (!items.isEmpty()) {
            return items.stream().mapToDouble(OrderReceiptItem::totalPrice).sum();
        }

        return null;
    }

    private boolean isTotalRow(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return (lower.contains("total paid") || lower.contains("grand total")
                || lower.contains("order total") || lower.contains("bill total")
                || lower.contains("paid via"))
                && !lower.contains("item total") && !lower.contains("item bill");
    }

    private boolean isSummaryOrHeader(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("total")
                || lower.contains("subtotal")
                || lower.contains("gst")
                || lower.contains("tax")
                || lower.contains("packaging")
                || lower.contains("delivery fee")
                || lower.contains("platform fee")
                || lower.contains("handling fee")
                || lower.contains("partner fee")
                || lower.contains("discount")
                || lower.contains("paid via")
                || lower.contains("item bill")
                || lower.contains("bill details")
                || lower.contains("order summary")
                || lower.contains("order items")
                || lower.contains("tip")
                || (lower.contains("item") && lower.contains("price"));
    }
}
