package io.github.harshjoeyit.cli.command;

import io.github.harshjoeyit.core.model.FileType;
import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.service.StatementService;
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

@Command(group = "Statement Parsing Commands")
public class StatementCommands {

    private final StatementService statementService;

    public StatementCommands(StatementService statementService) {
        this.statementService = statementService;
    }

    @Command(
            command = "parse",
            description = "Parse statement"
    )
    public String parse(
            @Option(longNames = "file", shortNames = 'f', required = true, description = "Absolute path to statement file: /Users/tminus1/Downloads/statement.csv") File file,
            @Option(longNames = "type", shortNames = 't', required = true, description = "Statement type (CC-Credit Card, ACC-Bank Account): HDFC_CC, HDFC_ACC, SCAPIA_CC") StatementType stmtType
    ) {
        // Validate if file exists
        if (!file.exists() || !file.isFile()) {
            return "Error: File not found: " + file.getAbsolutePath();
        }

        // Determine file type
        if (isPdf(file)) {
            return "Invalid file format. PDF is not supported yet";
        }

        try {
            List<NormalizedTransaction> transactions
                    = statementService.parseStatement(file, FileType.DELIMITED, stmtType);

            if (transactions.isEmpty()) {
                return "No transactions found in " + file.getName();
            }

            return renderTable(transactions, file.getName(), stmtType);
        } catch (Exception e) {
            return "Error parsing statement: " + e.getMessage();
        }
    }

    // Determines PDF uisng magic bytes in file header
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

        return String.format("%nParsed %d transactions from %s (%s):%n%n%s%n",
                transactions.size(), filename, type, renderedTable);
    }
}
