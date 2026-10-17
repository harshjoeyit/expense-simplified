package io.github.harshjoeyit.cli.command;

import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.Statement;
import io.github.harshjoeyit.service.InstrumentService;
import io.github.harshjoeyit.service.ReviewService;
import io.github.harshjoeyit.service.StatementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.table.ArrayTableModel;
import org.springframework.shell.table.BorderStyle;
import org.springframework.shell.table.TableBuilder;
import org.springframework.shell.table.TableModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Command(group = "Statement Parsing Commands")
public class StatementCommands {

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
    public String review(
            @Option(longNames = "statement", shortNames = 's', required = false, description = "Absolute path to statement file: /Users/tminus1/Downloads/statement.csv") UUID statementId
    ) {
        try {
            List<NormalizedTransaction> transactions = reviewService.findPendingReviewTransaction(statementId);
            if (transactions.isEmpty()) {
                return "All the transactions for this statement are already reviewed";
            }
            return "you need to review " + transactions.size() + " transactions";

            // Loop
                // Print a snapshot with required details for review only
                    // print if not null or blank: datetime, mode, counterparty (and name), amount (Dr./Cr.) (color = green/red basd on Dr./Cr.), comment, type, category
                // Review (capture user inputs)
                // Resolve

        } catch (Exception e) {
            log.error("Failed to review", e);
            return "Failed to review  Error: " + e.getMessage();
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
