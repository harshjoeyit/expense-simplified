package io.github.harshjoeyit.core.parse.hdfc.narration;

import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.model.NarrationParseResult;
import io.github.harshjoeyit.core.parse.model.NarrationType;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import org.springframework.stereotype.Component;

/**
 * Parses IMPS narration
 * IMPS-622725189133-HRITIJ GUPTA-BARB-XXXXXXXXXX6199-ARCHITECT
 */
@Component
public class ImpsNarrationParser implements NarrationParser {

    private static final String IMPSPrefix = "IMPS";
    private static final char separator = '-';

    @Override
    public boolean canParse(String narration) {
        return narration.startsWith(IMPSPrefix);
    }

    @Override
    public NarrationParseResult parse(String narration) throws MalformedNarrationException {
        if (!canParse(narration)) {
            throw new MalformedNarrationException("UPI txn narration should start with 'UPI'");
        }

        String []parts = narration.split("-");

        if (parts.length < 5) {
            throw new MalformedNarrationException("Invalid narration");
        }

        // name + bank short name + masked account number
        String counterpartyId = String.join("-", parts[2], parts[3], parts[4]);
        String userComment = parts.length > 5? parts[5]: null;

        return NarrationParseResult.builder()
                .narrationType(NarrationType.IMPS)
                .txnMode(TxnMode.IMPS)
                .counterparty(parts[2])
                .counterpartyId(counterpartyId)
                .bankReference(parts[1])
                .userComment(userComment)
                .build();
    }
}
