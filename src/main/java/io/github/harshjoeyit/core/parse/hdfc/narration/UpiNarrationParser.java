package io.github.harshjoeyit.core.parse.hdfc.narration;

import io.github.harshjoeyit.core.parse.NarrationTooling;
import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.model.NarrationParseResult;
import io.github.harshjoeyit.core.parse.model.NarrationType;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import org.springframework.stereotype.Service;

/**
 * Parses UPI transactions
 * Expected Format: UPI-<Counterparty Name>-<UPI Id>-<Bank IFSC>-<Bank Refence>-<User comment>
 * UPI-MANOJ KUMAR-1234567890-2@AXL-CNRB0000011-659929880029-Laundry-payment
 */
@Service
public class UpiNarrationParser implements NarrationParser {

    private final NarrationTooling narrationTooling;
    private static final String UPIPrefix = "UPI";
    private static final char separator = '-';

    public UpiNarrationParser(NarrationTooling narrationTooling) {
        this.narrationTooling = narrationTooling;
    }

    @Override
    public boolean canParse(String narration) {
        return narration.startsWith(UPIPrefix);
    }

    // UPI ID and comment can both have a "-", hence simple split wouldn't work
    @Override
    public NarrationParseResult parse(String narration) throws MalformedNarrationException {
        if (!canParse(narration)) {
            throw new MalformedNarrationException("UPI txn narration should start with 'UPI'");
        }
        if (narration.charAt(3) != separator) {
            throw new MalformedNarrationException("Separator '" + separator + "' expected after UPI");
        }

        // <Counterparty Name>-<UPI Id>-<Bank IFSC>-<Bank Refence>-<User comment>
        String prefixStrippedNarration = narration.substring(4);

        // Get the Counterparty name
        int nameUpiIDSeparatorIndex = prefixStrippedNarration.indexOf(separator);

        // Check if a separator actually exists to avoid errors
        if (nameUpiIDSeparatorIndex == -1) {
            throw new MalformedNarrationException("Separator '" + separator + "' expected after counterparty name");
        }

        String counterpartyName = prefixStrippedNarration.substring(0, nameUpiIDSeparatorIndex);
        System.out.println("counterpartyName: " + counterpartyName);

        // <UPI Id>-<Bank IFSC>-<Bank Refence>-<User comment>
        String counterPartyStrippedNarration = prefixStrippedNarration.substring(nameUpiIDSeparatorIndex+1);

        // UPI
        String UpiId = narrationTooling.extractPrefixUpiId(counterPartyStrippedNarration);
        System.out.println("UPI: " + UpiId);
        if (UpiId == null) {
            throw new MalformedNarrationException("Missing UPI ID");
        }

        if (counterPartyStrippedNarration.charAt(UpiId.length()) != separator) {
            throw new MalformedNarrationException("Separator '" + separator + "' expected after UPI ID");
        }

        // <Bank IFSC>-<Bank Refence>-<User comment>
        String upiStrippedNarration = counterPartyStrippedNarration.substring(UpiId.length() + 1);

        String bankIfsc = narrationTooling.extractBankIfsc(upiStrippedNarration);
        System.out.println("IFSC: " + bankIfsc);
        if (bankIfsc == null) {
            throw new MalformedNarrationException("Missing Bank IFSC");
        }

        if (upiStrippedNarration.charAt(bankIfsc.length()) != separator) {
            throw new MalformedNarrationException("Separator '" + separator + "' expected after Bank IFSC");
        }

        // <Bank Refence>-<User comment>
        String bankIfscStrippedNarration = upiStrippedNarration.substring(bankIfsc.length() + 1);

        System.out.println("bank ifsc stripped narration: "  + bankIfscStrippedNarration);

        String utr = narrationTooling.extract12DigUpiUtr(bankIfscStrippedNarration);
        System.out.println("Bank Ref (UTR): " + utr);
        if (utr == null) {
            throw new MalformedNarrationException("Missing Bank Ref");
        }

        String userComment = null;
        if (utr.length() != bankIfscStrippedNarration.length()) {
            if (bankIfscStrippedNarration.charAt(utr.length()) != separator) {
                throw new MalformedNarrationException("Separator '" + separator + "' expected after Bank IFSC");
            }
            userComment = bankIfscStrippedNarration.substring(utr.length() + 1);
        }

        return NarrationParseResult.builder()
                .narrationType(NarrationType.UPI)
                .txnMode(TxnMode.UPI)
                .counterparty(counterpartyName)
                .counterpartyId(UpiId)
                .bankReference(utr)
                .userComment(userComment)
                .build();
    }
}
