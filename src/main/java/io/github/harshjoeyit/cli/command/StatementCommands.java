package io.github.harshjoeyit.cli.command;

import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnType;
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
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.AbstractShellComponent;
import org.springframework.shell.table.ArrayTableModel;
import org.springframework.shell.table.BorderStyle;
import org.springframework.shell.table.TableBuilder;
import org.springframework.shell.table.TableModel;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Command(group = "Statement Parsing Commands")
public class StatementCommands extends AbstractShellComponent {

    private final StatementService statementService;
    private final InstrumentService instrumentService;
    private final ReviewService reviewService;

    public StatementCommands(
            StatementService statementService,
            InstrumentService instrumentService,
            ReviewService reviewService) {
        this.statementService = statementService;
        this.instrumentService = instrumentService;
        this.reviewService = reviewService;
    }

    /**
     * Todo: command to view statements (most recent at the top), can pass -n flag to limit the results. Default top 3.
     *  User can use this to find out statement Id (required by review command) and other details
     */

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

            // Convert TxnType to a List of Strings
            List<SelectorItem<String>> txnTypes = Arrays.stream(TxnType.values())
                    .map(txnType -> SelectorItem.of(txnType.name(), txnType.name()))
                    .toList();

            // Convert TxnCategory to a List of Strings
            List<SelectorItem<String>> txnCategories = Arrays.stream(TxnCategory.values())
                    .map(txnCategory -> SelectorItem.of(txnCategory.name(), txnCategory.name()))
                    .toList();

            int i = 0;
            while (i < transactions.size()) {
                NormalizedTransaction txn = transactions.get(i);

                String dir = txn.getDirection() == TxnDirection.CREDIT ? "Cr" : "Dr";
                String counterparty = txn.getCounterparty() + " (" + txn.getCounterpartyId() + ")";

                // Show transaction snapshot
                writer.println(String.format("%n[%d/%d] %s | %s | ₹%.2f (%s) | %s | %s | %s",
                        i + 1, transactions.size(), readbFriendlyDateTime(txn.getDatetime()), txn.getMode(),
                        txn.getAmount(), dir, counterparty, txn.getUserComment(), txn.getType()));
                writer.flush();

                // read navigation input
                ReviewNavAction action = readNavAction(bindingReader, keyMap);
//                writer.println();  // next line after keypress

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
                    Optional<String> selectedTxnType = promptSingleItem("Select type:", txnTypes, 6);
                    selectedTxnType.ifPresent(txnType -> {
                        // Another selector for categories if needed
                        if (requiresCategory(txnType)) {
                            Optional<String> selectedTxnCategory = promptSingleItem("Select category:", txnCategories, 6);
                            selectedTxnCategory.ifPresent(txnCategory -> reviewService.resolveTransaction(
                                    txn.getId(),
                                    TxnType.valueOf(txnType),
                                    TxnCategory.valueOf(txnCategory),
                                    null,
                                    null
                            ));
                            return;
                        }

                        // TxnCategory remains null
                        reviewService.resolveTransaction(
                                txn.getId(),
                                TxnType.valueOf(txnType),
                                null,
                                null,
                                null
                        );
                    });

                    writer.println("✓ Resolved");
                    writer.flush();

                    // next transaction
                    i++;
                }
            }

            writer.println("\n✓ Review completed");
            writer.flush();
        } catch (Exception e) {
            log.error("Failed to review", e);
            writer.println("Failed to review. Error: " + e.getMessage());
            writer.flush();
        }
    }

    private boolean requiresCategory(String txnType) {
        return TxnType.valueOf(txnType) == TxnType.PURCHASE
                || TxnType.valueOf(txnType) == TxnType.REFUND;
    }

    private String readbFriendlyDateTime(LocalDateTime dateTime) {
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
    private <T> Optional<T> promptSingleItem(String title, List<SelectorItem<T>> items, int maxItems) {
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

        String statementId = !transactions.isEmpty() ? transactions.get(0).getStatementId().toString() : null;

        return String.format("%nParsed %d transactions from %s (%s):%n%n%s%n%s%n",
                transactions.size(), filename, type, statementId, renderedTable);
    }
}
