package io.github.harshjoeyit.cli.command;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import io.github.harshjoeyit.split.model.TransactionSplit;
import io.github.harshjoeyit.split.repository.SplitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.table.ArrayTableModel;
import org.springframework.shell.table.BorderStyle;
import org.springframework.shell.table.TableBuilder;
import org.springframework.shell.table.TableModel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Command(group = "Split Commands")
public class SplitCommands {

    private final SplitRepository splitRepository;
    private final TransactionRepository transactionRepository;

    public SplitCommands(SplitRepository splitRepository, TransactionRepository transactionRepository) {
        this.splitRepository = splitRepository;
        this.transactionRepository = transactionRepository;
    }

    @Command(
            command = "split",
            description = "View and manage expense splits"
    )
    public String split(
            @Option(longNames = "limit", shortNames = 'n', defaultValue = "10", description = "Maximum number of splits to display") int limit,
            @Option(longNames = "statement", shortNames = 's', description = "Filter splits by statement ID") UUID statementId,
            @Option(longNames = "all", shortNames = 'a', description = "Show all splits including POSTED (default shows only PENDING)") boolean showAll,
            @Option(longNames = "mark-posted", description = "Mark a split as POSTED (recorded in Splitwise) using Split ID") UUID markPostedId
    ) {
        // Handle --mark-posted
        if (markPostedId != null) {
            Optional<TransactionSplit> optSplit = splitRepository.findById(markPostedId);
            if (optSplit.isEmpty()) {
                return "⚠ Split not found with ID: " + markPostedId;
            }
            splitRepository.markAsPosted(markPostedId);
            TransactionSplit split = optSplit.get();
            return String.format("✓ Split %s (%s owes ₹%.2f) marked as POSTED",
                    markPostedId, split.friendName(), split.amount());
        }

        // Retrieve splits
        List<TransactionSplit> splits;
        if (statementId != null) {
            List<NormalizedTransaction> txns = transactionRepository.findByStatementId(statementId);
            Set<UUID> txnIds = txns.stream().map(NormalizedTransaction::getId).collect(Collectors.toSet());
            List<TransactionSplit> baseSplits = showAll
                    ? splitRepository.findAll(Integer.MAX_VALUE)
                    : splitRepository.findPendingSplits(Integer.MAX_VALUE);
            splits = baseSplits.stream()
                    .filter(s -> txnIds.contains(s.transactionId()))
                    .limit(limit)
                    .toList();
        } else {
            splits = showAll
                    ? splitRepository.findAll(limit)
                    : splitRepository.findPendingSplits(limit);
        }

        if (splits.isEmpty()) {
            return showAll ? "No splits found." : "No pending splits found.";
        }

        return renderSplitsTable(splits);
    }

    private String renderSplitsTable(List<TransactionSplit> splits) {
        String[][] data = new String[splits.size() + 1][8];
        data[0] = new String[]{"Split ID", "Txn ID", "Date", "Counterparty", "Friend", "Amount (₹)", "Strategy", "Status"};

        for (int i = 0; i < splits.size(); i++) {
            TransactionSplit split = splits.get(i);
            Optional<NormalizedTransaction> optTxn = transactionRepository.findById(split.transactionId());

            String dateStr = optTxn.map(t -> formatDate(t.getDatetime()))
                    .orElseGet(() -> split.createdAt() != null ? formatDate(split.createdAt()) : "-");
            String counterparty = optTxn.map(NormalizedTransaction::getCounterparty).orElse("-");
            if (counterparty == null || counterparty.isBlank()) {
                counterparty = "-";
            }

            data[i + 1] = new String[]{
                    split.id() != null ? split.id().toString() : "-",
                    split.transactionId() != null ? split.transactionId().toString() : "-",
                    dateStr,
                    counterparty,
                    split.friendName() != null ? split.friendName() : split.friendId(),
                    String.format(Locale.ROOT, "₹%.2f", split.amount()),
                    split.strategy() != null ? split.strategy().name() : "-",
                    split.status() != null ? split.status().name() : "-"
            };
        }

        TableModel model = new ArrayTableModel(data);
        TableBuilder tableBuilder = new TableBuilder(model);
        tableBuilder.addFullBorder(BorderStyle.fancy_light);

        return String.format("%nSplits (%d shown):%n%s%n",
                splits.size(), tableBuilder.build().render(160));
    }

    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM (E)", Locale.ENGLISH);
        return dateTime.format(formatter);
    }
}
