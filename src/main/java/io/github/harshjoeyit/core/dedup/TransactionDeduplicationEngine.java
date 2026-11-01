package io.github.harshjoeyit.core.dedup;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Component
public class TransactionDeduplicationEngine {

    private final TransactionRepository transactionRepository;

    public TransactionDeduplicationEngine(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Deduplicates incoming transactions against existing (recorded).
     *
     * @param targetInstrumentId the instrument selected by the user for upload
     * @param incomingTxns       the newly parsed in-memory transactions
     * @return DeduplicationResult containing new transactions, duplicate matches,
     *         statement overlap summaries, and any cross-instrument collision warning
     */
    public DeduplicationResult deduplicate(String targetInstrumentId, List<NormalizedTransaction> incomingTxns) {
        if (incomingTxns == null || incomingTxns.isEmpty()) {
            return new DeduplicationResult(List.of(), List.of(), List.of(), null);
        }

        // Determine exact [minDate, maxDate] from incomingTxns
        // and expand to day boundaries (start of day to end of day) to ensure
        // time-of-day differences do not exclude boundary transactions
        LocalDateTime minDate = incomingTxns.get(0).getDatetime();
        LocalDateTime maxDate = incomingTxns.get(0).getDatetime();
        for (NormalizedTransaction txn : incomingTxns) {
            if (txn.getDatetime().isAfter(maxDate)) {
                maxDate = txn.getDatetime();
            }
            if (txn.getDatetime().isBefore(minDate)) {
                minDate = txn.getDatetime();
            }
        }

        LocalDateTime startOfDay = minDate.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = maxDate.toLocalDate().atTime(LocalTime.MAX);

        List<NormalizedTransaction> existingTxnsInDateRange = transactionRepository.findByDateRange(startOfDay, endOfDay);

        String collisionInstrument = checkForCrossInstrumentCollision(
                targetInstrumentId, incomingTxns, existingTxnsInDateRange);
        if (collisionInstrument != null) {
            log.warn("Cross-instrument collision detected: all {} transactions match instrument '{}'",
                    incomingTxns.size(), collisionInstrument);
            return new DeduplicationResult(List.of(), List.of(), List.of(), collisionInstrument);
        }

        // Perform multiset matching for same instrument
        SameInstrumentDuplicateCheckResult dupCheckResult = checkForSameInstrumentDuplicates(
                targetInstrumentId, incomingTxns, existingTxnsInDateRange);

        // Group duplicates by statementId into StatementOverlapSummary
        List<StatementOverlapSummary> statementSummaries = buildStatementOverlapSummary(dupCheckResult.duplicates);

        return new DeduplicationResult(dupCheckResult.newTransactions, dupCheckResult.duplicates, statementSummaries, null);
    }

    private record SameInstrumentDuplicateCheckResult(
            List<NormalizedTransaction> newTransactions,
            List<DuplicateMatch> duplicates
    ) { }

    private List<StatementOverlapSummary> buildStatementOverlapSummary(
            List<DuplicateMatch> duplicates
    ) {
        if (duplicates == null || duplicates.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<DuplicateMatch>> byStatementId = new LinkedHashMap<>();
        for (DuplicateMatch match : duplicates) {
            UUID stmtId = match.existing().getStatementId();
            if (stmtId != null) {
                byStatementId.computeIfAbsent(stmtId, k -> new ArrayList<>()).add(match);
            }
        }

        List<StatementOverlapSummary> summaries = new ArrayList<>();
        for (Map.Entry<UUID, List<DuplicateMatch>> entry : byStatementId.entrySet()) {
            UUID stmtId = entry.getKey();
            List<DuplicateMatch> matches = entry.getValue();

            String instrumentId = matches.get(0).existing().getInstrumentId();
            // count represents duplication matches in the statement
            int count = matches.size();

            LocalDate minDate = matches.stream()
                    .map(m -> m.existing().getDatetime().toLocalDate())
                    .min(LocalDate::compareTo)
                    .orElse(null);

            LocalDate maxDate = matches.stream()
                    .map(m -> m.existing().getDatetime().toLocalDate())
                    .max(LocalDate::compareTo)
                    .orElse(null);

            summaries.add(new StatementOverlapSummary(stmtId, instrumentId, count, minDate, maxDate));
        }

        return summaries;
    }

    /**
     * Performs multiset (frequency) matching using queues indexed by DuplicateKey.
     *
     * <p><b>Why a Queue?</b>
     * If a user has multiple legitimate identical transactions in the same period
     * (e.g. buying two coffees at Starbucks for ₹250 on the same day without a bank reference):
     * <ul>
     *   <li>Suppose the database already has 1 coffee (queue size = 1).</li>
     *   <li>The incoming statement has 2 coffees.</li>
     *   <li>When the 1st incoming coffee is evaluated, it matches and polls the head of the queue (marked duplicate).</li>
     *   <li>When the 2nd incoming coffee is evaluated, the queue is now empty, so it is correctly marked as NEW!</li>
     * </ul>
     * This 1-to-1 multiset consumption ensures identical transactions are never falsely marked as duplicates.
     */
    private SameInstrumentDuplicateCheckResult checkForSameInstrumentDuplicates(
            String targetInstrumentId,
            List<NormalizedTransaction> incomingTxns,
            List<NormalizedTransaction> existingTxnsInDateRange
    ) {
        List<NormalizedTransaction> sameInstrumentExistingTxns = existingTxnsInDateRange.stream()
                .filter(t -> t.getInstrumentId().equals(targetInstrumentId))
                .toList();

        Map<DuplicateKey, Queue<NormalizedTransaction>> dupKeyToTxns = new HashMap<>();
        for (NormalizedTransaction sameInsExTxn : sameInstrumentExistingTxns) {
            DuplicateKey key = DuplicateKey.from(sameInsExTxn);
            dupKeyToTxns.computeIfAbsent(key, k -> new LinkedList<>()).add(sameInsExTxn);
        }

        List<DuplicateMatch> duplicates = new ArrayList<>();
        List<NormalizedTransaction> newTransactions = new ArrayList<>();

        for (NormalizedTransaction inTxn : incomingTxns) {
            DuplicateKey key = DuplicateKey.from(inTxn);

            Queue<NormalizedTransaction> matchingDupKeyTxns = dupKeyToTxns.get(key);
            if (matchingDupKeyTxns != null && !matchingDupKeyTxns.isEmpty()) {
                NormalizedTransaction matchedExisting = matchingDupKeyTxns.poll();
                log.debug("Duplicate found: incoming [{}] matched existing [{}]", inTxn.getId(), matchedExisting.getId());
                duplicates.add(new DuplicateMatch(inTxn, matchedExisting));
            } else {
                newTransactions.add(inTxn);
            }
        }

        return new SameInstrumentDuplicateCheckResult(newTransactions, duplicates);
    }

    /**
     * Checks for cross-instrument collisions.
     *
     * <p><b>Why we require 100% of incoming transactions to match another instrument:</b>
     * Accidental statement upload for the wrong account is a batch event where all transactions
     * in the statement exist under the other instrument.
     *
     * <p>Requiring a full batch match prevents false positives on legitimate coincidental purchases
     * made across different accounts or cards without bank references (Tier 2 - counterparty name),
     * such as:
     * <ul>
     *   <li>Taking two ₹150 Uber rides in a day (morning on Card A, evening on Card B).</li>
     *   <li>Two ₹20 chai / coffee payments at a tapri on the same day across cards.</li>
     * </ul>
     * If only 1 transaction matches, it is almost certainly a legitimate coincidence and aborting
     * the statement would block the user from importing valid transactions.
     *
     * @return the name of the colliding instrument if all incoming transactions match it, otherwise null
     */
    private String checkForCrossInstrumentCollision(
            String targetInstrumentId,
            List<NormalizedTransaction> incomingTxns,
            List<NormalizedTransaction> existingTxnsInDateRange
    ) {
        List<NormalizedTransaction> otherInstrumentExistingTxns = existingTxnsInDateRange.stream()
                .filter(t -> !t.getInstrumentId().equals(targetInstrumentId))
                .toList();

        // No need for cross-instrument collision check if none of the existing transactions
        // in range belong to a different instrument
        if (otherInstrumentExistingTxns.isEmpty()) {
            return null;
        }

        // Dup key -> InstrumentId
        Map<DuplicateKey, String> txnDupKeyToInstrument = new HashMap<>();
        for (NormalizedTransaction otherInsExTxn : otherInstrumentExistingTxns) {
            txnDupKeyToInstrument.put(DuplicateKey.from(otherInsExTxn), otherInsExTxn.getInstrumentId());
        }

        // Instrument -> collision count
        Map<String, Integer> collisionCountsPerInstrument = new HashMap<>();

        for (NormalizedTransaction inTxn : incomingTxns) {
            DuplicateKey incomingTxnDupKey = DuplicateKey.from(inTxn);
            String instrumentId = txnDupKeyToInstrument.get(incomingTxnDupKey);
            if (instrumentId != null) {
                collisionCountsPerInstrument.merge(instrumentId, 1, Integer::sum);
            }
        }

        // Flag as collision only if all incoming transactions match another instrument
        for (Map.Entry<String, Integer> entry : collisionCountsPerInstrument.entrySet()) {
            if (entry.getValue() == incomingTxns.size()) {
                return entry.getKey();
            }
        }

        return null;
    }
}
