package io.github.harshjoeyit.cli.command;

import io.github.harshjoeyit.core.exception.InvalidMappingRule;
import io.github.harshjoeyit.core.exception.InvalidStatementId;
import io.github.harshjoeyit.core.exception.InvalidTransactionId;
import io.github.harshjoeyit.core.model.*;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnType;
import io.github.harshjoeyit.export.model.CsvExportInput;
import io.github.harshjoeyit.export.service.CsvExportService;
import io.github.harshjoeyit.service.InstrumentService;
import io.github.harshjoeyit.service.ReviewService;
import io.github.harshjoeyit.service.StatementService;
import lombok.extern.slf4j.Slf4j;
import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.utils.InfoCmp;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.component.SingleItemSelector;
import org.springframework.shell.component.StringInput;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.AbstractShellComponent;
import org.springframework.shell.table.ArrayTableModel;
import org.springframework.shell.table.BorderStyle;
import org.springframework.shell.table.TableBuilder;
import org.springframework.shell.table.TableModel;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Command(group = "Statement Parsing Commands")
public class StatementCommands extends AbstractShellComponent {

    private final StatementService statementService;
    private final InstrumentService instrumentService;
    private final ReviewService reviewService;
    private final CsvExportService csvExportService;

    public StatementCommands(
            StatementService statementService,
            InstrumentService instrumentService,
            ReviewService reviewService,
            CsvExportService csvExportService) {
        this.statementService = statementService;
        this.instrumentService = instrumentService;
        this.reviewService = reviewService;
        this.csvExportService = csvExportService;
    }

    @Command(
            command = "statements",
            description = "View uploaded statements (most recent first)"
    )
    public String listStatements(
            @Option(longNames = "limit", shortNames = 'n', defaultValue = "3", description = "Max statements to display (default: 3)") int limit
    ) {
        List<StatementService.StatementSummary> summaries = statementService.getStatementSummaries(limit);

        if (summaries.isEmpty()) {
            return "No statements found. Use 'parse' command to import a statement.";
        }

        return renderStatementsTable(summaries);
    }

    @Command(
            command = "parse",
            description = "Parse statement"
    )
    public String parse(
            @Option(longNames = "file", shortNames = 'f', required = true, description = "Absolute path to statement file: /Users/tminus1/Downloads/statement.csv") File file,
            @Option(longNames = "instrument", shortNames = 'i', required = true, description = "Instrument: Banks account, credit card identifier") String instrumentId
    ) {
        // Validate if file exists
        if (!file.exists() || !file.isFile()) {
            return "Error: File not found: " + file.getAbsolutePath();
        }

        // Determine file type
        if (isPdf(file)) {
            return "Invalid file format. PDF is not supported yet";
        }

        // Determine file statementType
        Optional<StatementType> statementType = instrumentService.getStatementType(instrumentId);
        if (statementType.isEmpty()) {
            return "Error: statement type could not be determined";
        }
        log.info("Using statement type: {}", statementType);

        try {
            // Create a statement
            Statement statement = statementService.createStatement(FileType.DELIMITED, file.getName(), instrumentId);

            // Parse transactions
            List<NormalizedTransaction> transactions = statementService.parseStatement(
                    file, FileType.DELIMITED, statementType.get(), instrumentId, statement.id());

            if (transactions.isEmpty()) {
                return "No transactions found in " + file.getName();
            }

            return renderTable(transactions, file.getName(), statementType.get());
        } catch (Exception e) {
            log.error("Error parsing statement", e);
            return "Error parsing statement: " + e.getMessage();
        }
    }

    @Command(
            command = "review",
            description = "Manually review transactions to update category, type, remarks, etc"
    )
    public void review(
            @Option(longNames = "statement", shortNames = 's', description = "Statement ID (Ex - 263672ac-6db2-4d00-b82c-fba61feb1df9)") UUID statementId
    ) {
        PrintWriter writer = getTerminal().writer();
        try {
            List<NormalizedTransaction> transactions = reviewService.findPendingReviewTransaction(statementId);

            if (transactions.isEmpty()) {
                writer.println("All transactions are reviewed.");
                getTerminal().flush();
                return;
            }

            BindingReader bindingReader = new BindingReader(getTerminal().reader());
            KeyMap<ReviewNavAction> keyMap = buildReviewKeyMap();

            List<SelectorItem<String>> txnTypeOptions = Arrays.stream(TxnType.values())
                    .map(txnType -> SelectorItem.of(txnType.name(), txnType.name()))
                    .toList();
            List<SelectorItem<String>> txnCategoryOptions = Arrays.stream(TxnCategory.values())
                    .map(txnCategory -> SelectorItem.of(txnCategory.name(), txnCategory.name()))
                    .toList();

            int i = 0;
            while (i < transactions.size()) {
                NormalizedTransaction txn = transactions.get(i);

                String dir = txn.getDirection() == TxnDirection.CREDIT ? "Cr" : "Dr";
                String counterparty = txn.getCounterparty() + " (" + txn.getCounterpartyId() + ")";

                // Show transaction snapshot
                writer.println(String.format("%n[%d/%d] %s | %s | ₹%.2f (%s) | %s | %s | %s",
                        i + 1, transactions.size(), readFriendlyDateTime(txn.getDatetime()), txn.getMode(),
                        txn.getAmount(), dir, counterparty, txn.getUserComment(), txn.getType()));
                writer.flush();

                // read navigation input
                ReviewNavAction action = readNavAction(bindingReader, keyMap);

                if (action == ReviewNavAction.QUIT) {
                    writer.println("✓ Progress saved");
                    writer.flush();
                    break;
                }

                if (action == ReviewNavAction.NEXT) {
                    writer.println("Skip to next");
                    writer.flush();
                    i++;
                    if (i == transactions.size()) {

                    }
                    continue;
                }

                if (action == ReviewNavAction.PREV) {
                    if (i > 0) {
                        writer.println("Skip to previous");
                        writer.flush();
                        i--;
                    } else {
                        writer.println("Already at the first transaction");
                        writer.flush();
                    }
                    continue;
                }

                if (action == ReviewNavAction.CATEGORIZE) {
                    TxnType txnType = null;
                    TxnCategory txnCategory = null;
                    String reviewRemarks;
                    SaveRule saveRule;

                    // Txn type
                    Optional<String> selectedTxnType = promptSelectSingleItem(
                            "Select type:", txnTypeOptions, 5);
                    if (selectedTxnType.isPresent()) {
                        txnType = TxnType.valueOf(selectedTxnType.get());

                        // Category
                        if (requiresCategory(txnType)) {
                            Optional<String> selectedTxnCategory = promptSelectSingleItem(
                                    "Select category:", txnCategoryOptions, 5);
                            if (selectedTxnCategory.isPresent()) {
                                txnCategory = TxnCategory.valueOf(selectedTxnCategory.get());
                            }
                        }
                    }

                    // Review remarks
                    reviewRemarks = promptString("Review remarks (optional):", null).orElse(null);
                    // Save rule for future (optional)
                    saveRule = promptSaveRule(txn).orElse(null);

                    try {
                        //  Resolve transaction
                        reviewService.resolveTransaction(txn.getId(), txnType, txnCategory, reviewRemarks, saveRule);
                        writer.println("✓ Resolved");
                    } catch (InvalidTransactionId e) {
                        writer.println("⚠ Invalid transaction (skipping): " + e.getMessage());
                        log.warn("Invalid transaction {}", txn.getId(), e);
                    } catch (InvalidStatementId e) {
                        writer.println("⚠ Invalid statement (skipping): " + e.getMessage());
                        log.warn("Invalid statement id {} for {}", txn.getStatementId(), txn.getId(), e);
                    } catch (InvalidMappingRule e) {
                        writer.println("⚠ Invalid mapping rule (skipping): " + e.getMessage());
                        log.warn("Invalid mapping id {}", txn.getId(), e);
                    } catch (UnsupportedOperationException e) {
                        writer.println("⚠ Invalid rule: " + e.getMessage());
                        log.warn("Unsupported operation {}", txn.getId(), e);
                    } catch (Exception e) {
                        writer.println("⚠ Unexpected error: " + e.getMessage());
                        log.warn("Unexpected error {}", txn.getId(), e);
                    } finally {
                        writer.flush();
                        i++; // next transaction
                    }
                }
            }

            if (i == transactions.size()) {
                writer.println("\n✓ Review completed");
            } else  {
                writer.println("\n ⌛ [" + (transactions.size() - i) + "/" + transactions.size() +
                        "] transactions pending to be reviewed");
            }
        } catch (NoSuchElementException e) {
            writer.println("Statement not found. ");
            log.error("Failed to review", e);
        } catch (Exception e) {
            writer.println("Failed to review. Error: " + e.getMessage());
            log.error("Failed to review", e);
        } finally {
            writer.flush();
        }
    }

    @Command(
            command = "export",
            description = "Export reviewed transactions into CSV"
    )
    public String export(
            @Option(longNames = "statement", shortNames = 's', description = "Statement ID (Ex - 263672ac-6db2-4d00-b82c-fba61feb1df9)") UUID statementId,
            @Option(longNames = "from", shortNames = 'f', description = "Period start date (YYYY-MM-DD)") LocalDate periodStartDate,
            @Option(longNames = "to", shortNames = 't', description = "Period end date (YYYY-MM-DD)") LocalDate periodEndDate,
            @Option(longNames = "profile", shortNames = 'p', defaultValue = "default", description = "Export profile name (e.g. default, notion)") String profile,
            @Option(longNames = "output-dir", shortNames = 'o', description = "Target directory (default: ./export)") String outputDir
    ) {
        if (statementId == null && (periodStartDate == null || periodEndDate == null)) {
            // need at least a statement or time window
            return "Either statement Id or time window [from, to] required";
        }

        CsvExportInput input = CsvExportInput.builder()
                .statementId(statementId)
                .periodStartDate(periodStartDate)
                .periodEndDate(periodEndDate)
                .build();

        try {
            csvExportService.export(input, profile, outputDir);
            return "✓ Export completed successfully to " + (outputDir != null ? outputDir : "./export");
        } catch (IllegalArgumentException e) {
            return "Export failed: " + e.getMessage();
        } catch (Exception e) {
            log.error("Export failed", e);
            return "Export failed: " + e.getMessage();
        }
    }

    private boolean requiresCategory(TxnType txnType) {
        return txnType == TxnType.PURCHASE
                || txnType == TxnType.REFUND;
    }

    private String readFriendlyDateTime(LocalDateTime dateTime) {
        // 'd' = day of month, 'MMM' = short month name, 'E' = short day of the week
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM (E)", Locale.ENGLISH);
        // Format the date
        String readableDate = dateTime.format(formatter);
        // Output: 14 Aug (Fri)
        return readableDate;
    }

    /**
     * Helper method reusing AbstractShellComponent getters
     */
    private <T> Optional<T> promptSelectSingleItem(String title, List<SelectorItem<T>> items, int maxItems) {
        SingleItemSelector<T, SelectorItem<T>> component = new SingleItemSelector<>(
                getTerminal(),
                items,
                title,
                null
        );

        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());
        if (maxItems > 0) {
            component.setMaxItems(maxItems);
        }

        SingleItemSelector.SingleItemSelectorContext<T, SelectorItem<T>> context =
                component.run(SingleItemSelector.SingleItemSelectorContext.empty());

        return context.getResultItem().map(SelectorItem::getItem);
    }

    private Optional<String> promptString(String title, String defaultValue) {
        StringInput component = new StringInput(getTerminal(), title, defaultValue);
        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());

        StringInput.StringInputContext context = component.run(StringInput.StringInputContext.empty());
        String result = context.getResultValue();
        return Optional.ofNullable(result).filter(s -> !s.isBlank());
    }

    private Optional<SaveRule> promptSaveRule(NormalizedTransaction txn) {
        List<SelectorItem<String>> options = new ArrayList<>();

        // 1. Don't save
        options.add(SelectorItem.of("Don't save rule", "NONE"));

        // 2. Save by Counterparty ID
        boolean hasCounterpartyId = txn.getCounterpartyId() != null && !txn.getCounterpartyId().isBlank();
        if (hasCounterpartyId) {
            options.add(SelectorItem.of("Save by Counterparty ID ("
                    + txn.getCounterpartyId() + ")", "BY_ID" + txn.getCounterpartyId()));
        }

        // 3. Save by Counterparty Name
        boolean hasCounterpartyName = txn.getCounterparty() != null && !txn.getCounterparty().isBlank();
        if (hasCounterpartyName) {
            options.add(SelectorItem.of("Save by Counterparty Name ("
                    + txn.getCounterparty() + ")", "BY_NAME: " + txn.getCounterparty()));
        }

        // If neither counterparty name nor Id exists, skip rule prompt entirely
        if (options.size() == 1) {
            return Optional.empty();
        }

        Optional<String> selected = promptSelectSingleItem("Save rule for future transactions?", options, 5);
        if (selected.isEmpty() || "NONE".equals(selected.get())) {
            return Optional.empty();
        }

        if (selected.get().startsWith("BY_ID")) {
            return Optional.of(SaveRule.builder()
                    .saveRuleByType(SaveRule.SaveRuleByType.COUNTERPARTY_ID)
                    .id(txn.getCounterpartyId())
                    .autoReview(true)
                    .build());
        }

        if (selected.get().startsWith("BY_NAME")) {
            return Optional.of(SaveRule.builder()
                    .saveRuleByType(SaveRule.SaveRuleByType.COUNTERPARTY_NAME)
                    .id(txn.getCounterparty())
                    .autoReview(true)
                    .build());
        }

        return Optional.empty();
    }

    private enum ReviewNavAction {
        CATEGORIZE,
        NEXT,
        PREV,
        QUIT
    }

    /**
     * Map allowed actions to keystrokes
     */
    private KeyMap<ReviewNavAction> buildReviewKeyMap() {
        KeyMap<ReviewNavAction> keyMap = new KeyMap<>();

        // Next/Skip: Right arrow, 'n', 'N'
        keyMap.bind(ReviewNavAction.NEXT, "\033[C", "\033OC", "n", "N");
        String keyRight = KeyMap.key(getTerminal(), InfoCmp.Capability.key_right);
        if (keyRight != null) {
            keyMap.bind(ReviewNavAction.NEXT, keyRight);
        }

        // Prev/Back: Left arrow, 'p', 'P'
        keyMap.bind(ReviewNavAction.PREV, "\033[D", "\033OD", "p", "P");
        String keyLeft = KeyMap.key(getTerminal(), InfoCmp.Capability.key_left);        // fallback
        if (keyLeft != null) {
            keyMap.bind(ReviewNavAction.PREV, keyLeft);
        }

        // Categorize: Enter, 'c', 'C'
        keyMap.bind(ReviewNavAction.CATEGORIZE, "\r", "\n", "c", "C");

        // Quit: 'q', 'Q', Esc
        keyMap.bind(ReviewNavAction.QUIT, "q", "Q", "\033");

        return keyMap;
    }

    /**
     * Read a single navigation keystroke instantly (without waiting for the user to press Enter)
     * and safely restore the terminal afterward
     */
    private ReviewNavAction readNavAction(BindingReader bindingReader, KeyMap<ReviewNavAction> keyMap) {
        Attributes prev = getTerminal().enterRawMode();
        try {
            ReviewNavAction action = null;
            // Loop is required since user can press any random characters on the keyboard but we want to only
            // capture what's in our keymap
            while (action == null) {
                action = bindingReader.readBinding(keyMap);
            }
            return action;
        } finally {
            getTerminal().setAttributes(prev);
        }
    }

    // Determines PDF using magic bytes in file header
    private boolean isPdf(File file) {
        byte[] header = new byte[5];
        try (InputStream is = new FileInputStream(file)) {
            int read = is.read(header);
            if (read < 5) return false;
            // Check for ASCII "%PDF-"
            return header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
        } catch (IOException e) {
            return false;
        }
    }

    private String renderTable(List<NormalizedTransaction> transactions, String filename, StatementType type) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // Prepare 2D data grid: (N + 1) rows, 7 columns
        String[][] data = new String[transactions.size() + 1][7];
        data[0] = new String[]{
                "Date/Time", "Amount (₹)", "Flow", "Type", "Mode", "Counterparty ID",
                "Counterparty", "Counterparty Type", "Comment", "Category", "Status"};

        for (int i = 0; i < transactions.size(); i++) {
            NormalizedTransaction txn = transactions.get(i);
            data[i + 1] = new String[]{
                    txn.getDatetime() != null ? txn.getDatetime().format(dtf) : "-",
                    txn.getAmount() != null ? String.format("%.2f", txn.getAmount()) : "0.00",
                    txn.getDirection() != null ? txn.getDirection().name() : "-",
                    txn.getType() != null ? txn.getType().name() : "-",
                    txn.getMode() != null ? txn.getMode().name() : "-",
                    txn.getCounterpartyId() != null ? txn.getCounterpartyId() : "-",
                    txn.getCounterparty() != null ? txn.getCounterparty() : "-",
                    txn.getCounterpartyType() != null ? txn.getCounterpartyType().name() : "-",
                    txn.getUserComment() != null ? txn.getUserComment() : "-",
                    txn.getCategory() != null ? txn.getCategory().name() : "UNASSIGNED",
                    txn.getReviewStatus() != null ? txn.getReviewStatus().name() : "-"
            };
        }

        TableModel model = new ArrayTableModel(data);
        TableBuilder tableBuilder = new TableBuilder(model);

        // Add styling (fancy_light uses clean Unicode box characters: ┌─┬─┐)
        tableBuilder.addFullBorder(BorderStyle.fancy_light);

        // Render with max terminal width (e.g., 120 columns)
        String renderedTable = tableBuilder.build().render(140);

        UUID statementId = !transactions.isEmpty() ? transactions.get(0).getStatementId() : null;

//        return String.format("%nParsed %d transactions from %s (%s):%n%n%s%n%s%n",
//                transactions.size(), filename, type, statementId, renderedTable);

        int total = transactions.size();
        int pendingReview = reviewService.findPendingReviewTransaction(statementId).size();

        return String.format("%nStatementId: %s%nParsed %d transactions from %s (%s).%n[%s/%s] need review %n",
                statementId, total, filename, type, pendingReview, total);
    }

    private String renderStatementsTable(List<StatementService.StatementSummary> summaries) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        String[][] data = new String[summaries.size() + 1][6];
        data[0] = new String[]{"Statement ID", "Instrument", "Uploaded At", "File", "Status", "Review Progress"};

        for (int i = 0; i < summaries.size(); i++) {
            StatementService.StatementSummary summary = summaries.get(i);
            Statement stmt = summary.statement();

            String createdAtStr = stmt.createdAt() != null ? stmt.createdAt().format(dtf) : "-";
            String filename = stmt.filePath() != null ? new File(stmt.filePath()).getName() : "-";
            String status = stmt.reviewStatus() != null ? stmt.reviewStatus().name() : "-";
            String progress = summary.pendingTransactions() == 0
                    ? "✓ Completed (" + summary.totalTransactions() + ")"
                    : summary.pendingTransactions() + " / " + summary.totalTransactions() + " pending";

            data[i + 1] = new String[]{
                    stmt.id() != null ? stmt.id().toString() : "-",
                    stmt.instrumentId() != null ? stmt.instrumentId() : "-",
                    createdAtStr,
                    filename,
                    status,
                    progress
            };
        }

        TableModel model = new ArrayTableModel(data);
        TableBuilder tableBuilder = new TableBuilder(model);
        tableBuilder.addFullBorder(BorderStyle.fancy_light);

        return String.format("%nStatements (%d shown):%n%s%n",
                summaries.size(), tableBuilder.build().render(140));
    }
}
