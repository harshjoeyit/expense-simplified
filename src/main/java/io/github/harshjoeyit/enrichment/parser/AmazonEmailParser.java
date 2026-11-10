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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AmazonEmailParser implements EmailReceiptParser {

    public static final String DEFAULT_OUTLET_NAME = "Amazon.in";

    // Example Order id: 404-6960323-3558726
    private static final Pattern AMAZON_ORDER_ID_PATTERN = Pattern.compile(
            "\\b([0-9]{3}-[0-9]{7}-[0-9]{7})\\b"
    );

    private static final Pattern PROMO_CUTOFF_PATTERN = Pattern.compile(
            "(?i)(?:Buy\\s+it\\s+again|Continue\\s+shopping\\s+deals|Deals\\s+inspired|Customers\\s+who\\s+bought)"
    );

    private static final Pattern TEXT_PROMO_CUTOFF_PATTERN = Pattern.compile(
            "(?i)(?:©\\s*[0-9]{4}|Deals\\s+inspired|Continue\\s+shopping|Customers\\s+who|Buy\\s+it\\s+again|Privacy\\s+Policy)"
    );

    private static final Pattern ARIA_LABEL_AMOUNT_PATTERN = Pattern.compile(
            "aria-label=[\"']\\{amount=([0-9.]+)"
    );

    private static final Pattern QTY_PATTERN = Pattern.compile(
            "(?i)Quantity:\\s*([0-9]+)"
    );

    private static final Pattern HTML_TOTAL_PATTERN = Pattern.compile(
            "(?i)Total</td>\\s*<td[^>]*>.*?([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.DOTALL
    );

    private static final Pattern GENERIC_TOTAL_PATTERN = Pattern.compile(
            "(?i)(?:order\\s+total|grand\\s+total|total\\s+for\\s+this\\s+shipment|total)\\s*(?:₹|INR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"
    );

    private static final Pattern TEXT_ITEM_PATTERN = Pattern.compile(
            "\\*\\s+([^\\n\\r]+(?:\\n[^\\n\\r]+)*?)\\s*\\n\\s*Quantity:\\s*([0-9]+)\\s*\\n\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:INR|₹)"
    );

    @Override
    public String platform() {
        return "AMAZON";
    }

    @Override
    public boolean supports(String sender, String subject) {
        String s = sender != null ? sender.toLowerCase(Locale.ROOT) : "";
        String sub = subject != null ? subject.toLowerCase(Locale.ROOT) : "";
        return s.contains("amazon.in") || s.contains("amazon.com")
                || sub.contains("amazon.in") || sub.contains("amazon.com");
    }

    @Override
    public Optional<OrderReceipt> parse(String sender, String subject, String bodyContent, LocalDateTime emailDate) {
        if (!supports(sender, subject) || bodyContent == null || bodyContent.isBlank()) {
            return Optional.empty();
        }

        boolean isHtml = bodyContent.contains("<html")
                || bodyContent.contains("<table")
                || bodyContent.contains("<div");
        if (isHtml) {
            Optional<OrderReceipt> htmlResult = parseHtml(bodyContent, emailDate);
            if (htmlResult.isPresent()) {
                return htmlResult;
            }
        }

        return parseText(bodyContent, emailDate);
    }

    private Optional<OrderReceipt> parseHtml(String html, LocalDateTime emailDate) {
        // Cut off promotional and recommendation sections
        String cleanHtml = html;
        Matcher cutoffMatcher = PROMO_CUTOFF_PATTERN.matcher(html);
        if (cutoffMatcher.find()) {
            cleanHtml = html.substring(0, cutoffMatcher.start());
        }

        // Identify all Order ID fulfillment blocks
        List<String> orderIds = new ArrayList<>();
        List<Integer> orderOffsets = new ArrayList<>();
        Matcher idMatcher = AMAZON_ORDER_ID_PATTERN.matcher(cleanHtml);
        while (idMatcher.find()) {
            String orderId = idMatcher.group(1);
            if (!orderIds.contains(orderId)) {
                orderIds.add(orderId);
                orderOffsets.add(idMatcher.start());
            }
        }

        if (orderIds.isEmpty()) {
            return parseFallbackTable(cleanHtml, emailDate);
        }

        List<OrderReceiptItem> allItems = new ArrayList<>();
        double totalSum = 0.0;

        for (int i = 0; i < orderIds.size(); i++) {
            int start = orderOffsets.get(i);
            int end = (i + 1 < orderOffsets.size()) ? orderOffsets.get(i + 1) : cleanHtml.length();
            String block = cleanHtml.substring(start, end);

            // Extract items in this fulfillment block
            List<OrderReceiptItem> blockItems = extractHtmlBlockItems(block);
            allItems.addAll(blockItems);

            // Extract total for this block
            double blockTotal = extractHtmlBlockTotal(block, blockItems);
            totalSum += blockTotal;
        }

        if (totalSum <= 0 && !allItems.isEmpty()) {
            totalSum = allItems.stream().mapToDouble(OrderReceiptItem::totalPrice).sum();
        }

        if (totalSum <= 0) {
            return parseFallbackTable(cleanHtml, emailDate);
        }

        // Round aggregate total to 2 decimal places (e.g. 723.00)
        double roundedTotal = Math.round(totalSum * 100.0) / 100.0;

        return Optional.of(OrderReceipt.builder()
                .platform(platform())
                .platformOrderId(String.join(", ", orderIds))
                .outletName(DEFAULT_OUTLET_NAME)
                .orderDateTime(emailDate != null ? emailDate : LocalDateTime.now())
                .totalAmount(roundedTotal)
                .items(allItems)
                .build());
    }

    private List<OrderReceiptItem> extractHtmlBlockItems(String blockHtml) {
        List<OrderReceiptItem> items = new ArrayList<>();

        Matcher imgMatcher = Pattern.compile("<img[^>]+class=[\"'][^\"']*productImage[^\"']*[\"']", Pattern.CASE_INSENSITIVE).matcher(blockHtml);
        List<Integer> imgStarts = new ArrayList<>();
        while (imgMatcher.find()) {
            imgStarts.add(imgMatcher.start());
        }

        if (!imgStarts.isEmpty()) {
            for (int i = 0; i < imgStarts.size(); i++) {
                int start = imgStarts.get(i);
                int end = (i + 1 < imgStarts.size()) ? imgStarts.get(i + 1) : Math.min(blockHtml.length(), start + 4000);
                String chunk = blockHtml.substring(start, end);

                Matcher altMatcher = Pattern.compile("alt=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(chunk);
                String title = "";
                if (altMatcher.find()) {
                    title = org.jsoup.parser.Parser.unescapeEntities(altMatcher.group(1).trim(), false);
                } else {
                    Matcher altSingle = Pattern.compile("alt='([^']+)'", Pattern.CASE_INSENSITIVE).matcher(chunk);
                    if (altSingle.find()) {
                        title = org.jsoup.parser.Parser.unescapeEntities(altSingle.group(1).trim(), false);
                    }
                }
                if (title.isBlank() || isNoiseTitle(title)) {
                    continue;
                }

                int qty = 1;
                Matcher qm = QTY_PATTERN.matcher(chunk);
                if (qm.find()) {
                    qty = EmailParserUtils.parseQuantity(qm.group(1));
                }

                double price = 0.0;
                Matcher am = ARIA_LABEL_AMOUNT_PATTERN.matcher(chunk);
                if (am.find()) {
                    price = EmailParserUtils.parsePrice(am.group(1)).orElse(0.0);
                }

                if (price <= 0) {
                    Document sDoc = Jsoup.parse(chunk);
                    Optional<Double> pOpt = EmailParserUtils.parsePrice(sDoc.text());
                    if (pOpt.isPresent()) {
                        price = pOpt.get();
                    }
                }

                items.add(OrderReceiptItem.builder()
                        .itemName(title)
                        .quantity(qty)
                        .unitPrice(price > 0 ? price / qty : null)
                        .totalPrice(price)
                        .build());
            }
        }

        if (items.isEmpty()) {
            // Fallback for simple/legacy HTML tables
            Document blockDoc = Jsoup.parse(blockHtml);
            for (Element row : blockDoc.select("tr")) {
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

                        String cleanName = col0.split("(?i)(sold by|condition:|quantity:|qty:)")[0].trim();
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

        return items;
    }

    private double extractHtmlBlockTotal(String blockHtml, List<OrderReceiptItem> blockItems) {
        Matcher tm = HTML_TOTAL_PATTERN.matcher(blockHtml);
        if (tm.find()) {
            Optional<Double> p = EmailParserUtils.parsePrice(tm.group(1));
            if (p.isPresent() && p.get() > 0) {
                return p.get();
            }
        }

        Document blockDoc = Jsoup.parse(blockHtml);
        Matcher gm = GENERIC_TOTAL_PATTERN.matcher(blockDoc.text());
        if (gm.find()) {
            Optional<Double> p = EmailParserUtils.parsePrice(gm.group(1));
            if (p.isPresent() && p.get() > 0) {
                return p.get();
            }
        }

        return blockItems.stream().mapToDouble(OrderReceiptItem::totalPrice).sum();
    }

    private Optional<OrderReceipt> parseFallbackTable(String html, LocalDateTime emailDate) {
        Document doc = Jsoup.parse(html);
        String bodyText = doc.text();

        String orderId = EmailParserUtils.matchFirst(bodyText, AMAZON_ORDER_ID_PATTERN, 1).orElse(null);

        List<OrderReceiptItem> items = new ArrayList<>();
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

                    String cleanName = col0.split("(?i)(sold by|condition:|quantity:|qty:)")[0].trim();
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

        double total = 0.0;
        for (Element row : doc.select("tr")) {
            String rowText = row.text().toLowerCase(Locale.ROOT);
            if (rowText.contains("order total") || rowText.contains("grand total")
                    || rowText.contains("total for this shipment") || rowText.contains("total")) {
                Elements cols = row.select("td, th");
                if (!cols.isEmpty()) {
                    Optional<Double> p = EmailParserUtils.parsePrice(cols.last().text());
                    if (p.isPresent() && p.get() > 0) {
                        total = p.get();
                        break;
                    }
                }
            }
        }

        if (total <= 0 && !items.isEmpty()) {
            total = items.stream().mapToDouble(OrderReceiptItem::totalPrice).sum();
        }

        if (total <= 0) {
            return Optional.empty();
        }

        return Optional.of(OrderReceipt.builder()
                .platform(platform())
                .platformOrderId(orderId)
                .outletName(DEFAULT_OUTLET_NAME)
                .orderDateTime(emailDate != null ? emailDate : LocalDateTime.now())
                .totalAmount(total)
                .items(items)
                .build());
    }

    private Optional<OrderReceipt> parseText(String text, LocalDateTime emailDate) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        // Skip header navigation (find first Order #)
        int orderIdx = -1;
        Matcher firstOrderMatcher = Pattern.compile("(?i)Order\\s*#").matcher(text);
        if (firstOrderMatcher.find()) {
            orderIdx = firstOrderMatcher.start();
        }

        String orderSection = (orderIdx != -1) ? text.substring(orderIdx) : text;

        // Cut off promotional footer
        Matcher cutoffMatcher = TEXT_PROMO_CUTOFF_PATTERN.matcher(orderSection);
        if (cutoffMatcher.find()) {
            orderSection = orderSection.substring(0, cutoffMatcher.start());
        }

        // Find all Order IDs
        List<String> orderIds = new ArrayList<>();
        List<Integer> orderOffsets = new ArrayList<>();
        Matcher idMatcher = AMAZON_ORDER_ID_PATTERN.matcher(orderSection);
        while (idMatcher.find()) {
            String oid = idMatcher.group(1);
            if (!orderIds.contains(oid)) {
                orderIds.add(oid);
                orderOffsets.add(idMatcher.start());
            }
        }

        if (orderIds.isEmpty()) {
            return Optional.empty();
        }

        List<OrderReceiptItem> allItems = new ArrayList<>();
        double totalSum = 0.0;

        for (int i = 0; i < orderIds.size(); i++) {
            int start = orderOffsets.get(i);
            int end = (i + 1 < orderOffsets.size()) ? orderOffsets.get(i + 1) : orderSection.length();
            String block = orderSection.substring(start, end);

            // Extract items: lines matching * <title>\n Quantity: <n>\n <price> INR
            Matcher im = TEXT_ITEM_PATTERN.matcher(block);
            List<OrderReceiptItem> blockItems = new ArrayList<>();
            while (im.find()) {
                String name = im.group(1).replaceAll("\\s+", " ").trim();
                int qty = EmailParserUtils.parseQuantity(im.group(2));
                double price = EmailParserUtils.parsePrice(im.group(3)).orElse(0.0);
                blockItems.add(OrderReceiptItem.builder()
                        .itemName(name)
                        .quantity(qty)
                        .unitPrice(price / qty)
                        .totalPrice(price)
                        .build());
            }
            allItems.addAll(blockItems);

            // Extract Total
            Matcher tm = Pattern.compile("(?i)Total\\s*\\n?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:INR|₹)?").matcher(block);
            double blockTotal = 0.0;
            if (tm.find()) {
                Optional<Double> p = EmailParserUtils.parsePrice(tm.group(1));
                if (p.isPresent()) {
                    blockTotal = p.get();
                }
            }
            if (blockTotal <= 0) {
                blockTotal = blockItems.stream().mapToDouble(OrderReceiptItem::totalPrice).sum();
            }
            totalSum += blockTotal;
        }

        if (totalSum <= 0 && !allItems.isEmpty()) {
            totalSum = allItems.stream().mapToDouble(OrderReceiptItem::totalPrice).sum();
        }

        if (totalSum <= 0) {
            return Optional.empty();
        }

        double roundedTotal = Math.round(totalSum * 100.0) / 100.0;

        return Optional.of(OrderReceipt.builder()
                .platform(platform())
                .platformOrderId(String.join(", ", orderIds))
                .outletName(DEFAULT_OUTLET_NAME)
                .orderDateTime(emailDate != null ? emailDate : LocalDateTime.now())
                .totalAmount(roundedTotal)
                .items(allItems)
                .build());
    }

    private boolean isNoiseTitle(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        return lower.contains("amazon")
                || lower.contains("delivered")
                || lower.contains("shipped")
                || lower.contains("ordered")
                || lower.contains("rating")
                || lower.contains("star");
    }

    private boolean isSummaryOrNoise(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("order total")
                || lower.contains("subtotal")
                || lower.contains("shipping & handling")
                || lower.contains("free delivery")
                || lower.contains("promotion applied")
                || lower.contains("estimated tax")
                || lower.contains("gift card")
                || lower.contains("pay balance")
                || lower.contains("payment method")
                || lower.contains("delivery address");
    }
}
