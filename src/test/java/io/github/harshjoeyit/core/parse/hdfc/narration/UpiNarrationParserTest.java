package io.github.harshjoeyit.core.parse.hdfc.narration;

import io.github.harshjoeyit.core.parse.NarrationTooling;
import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.model.NarrationParseResult;
import io.github.harshjoeyit.core.parse.model.NarrationType;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpiNarrationParserTest {

    private UpiNarrationParser upiNarrationParser;

    @BeforeEach
    void setUp() {
        NarrationTooling narrationTooling = new NarrationTooling();
        upiNarrationParser = new UpiNarrationParser(narrationTooling);
    }

    @Test
    void parse_validNarrationWithoutUserComment_returnsParsedResultsWithoutUserComment() {
        String narration = "UPI-MANOJ KUMAR-1234567890-2@AXL-CNRB0000011-659929880029";
        NarrationParseResult result = upiNarrationParser.parse(narration);

        assertThat(result.getNarrationType()).isEqualTo(NarrationType.UPI);
        assertThat(result.getTxnMode()).isEqualTo(TxnMode.UPI);
        assertThat(result.getCounterparty()).isEqualTo("MANOJ KUMAR");
        assertThat(result.getCounterpartyId()).isEqualTo("1234567890-2@AXL");
        assertThat(result.getCounterpartyType()).isNull();
        assertThat(result.getBankReference()).isEqualTo("659929880029");
        assertThat(result.getUserComment()).isNull();
    }

    @Test
    void parse_validNarrationWithUserComment_returnsParsedResultWithUserComment() {
        String narration = "UPI-MANOJ KUMAR-1234567890-2@AXL-CNRB0000011-659929880029-Laundary-payment";
        NarrationParseResult result = upiNarrationParser.parse(narration);

        assertThat(result.getNarrationType()).isEqualTo(NarrationType.UPI);
        assertThat(result.getTxnMode()).isEqualTo(TxnMode.UPI);
        assertThat(result.getCounterparty()).isEqualTo("MANOJ KUMAR");
        assertThat(result.getCounterpartyId()).isEqualTo("1234567890-2@AXL");
        assertThat(result.getCounterpartyType()).isNull();
        assertThat(result.getBankReference()).isEqualTo("659929880029");
        assertThat(result.getUserComment()).isEqualTo("Laundary-payment");
    }

    @Test
    void parse_narrationWithoutUpiPrefix_throwsMalformedException() {
        String narration = "TP-MANOJ KUMAR-123";

        assertThatThrownBy(() -> upiNarrationParser.parse(narration))
                .isInstanceOf(MalformedNarrationException.class)
                .hasMessage("UPI txn narration should start with 'UPI'");
    }

    @Test
    void parse_narrationWithoutUpiID_throwsMalformedException() {
        String narration = "UPI-MANOJ KUMAR-CNRB0000011-659929880029-Laundary-payment";

        assertThatThrownBy(() -> upiNarrationParser.parse(narration))
                .isInstanceOf(MalformedNarrationException.class)
                .hasMessage("Missing UPI ID");
    }

    @Test
    void parse_narrationWithoutSeparatorBetweenUpiPrefixAndName_throwsMalformedException() {
        String narration = "UPIMANOJ KUMAR-1234567890-2@AXL-CNRB0000011-659929880029-Laundary-payment";

        assertThatThrownBy(() -> upiNarrationParser.parse(narration))
                .isInstanceOf(MalformedNarrationException.class)
                .hasMessage("Separator '-' expected after UPI");
    }

    @Test
    void parse_narrationWithoutBankRef_throwsMalformedException() {
        String narration = "UPI-MANOJ KUMAR-1234567890-2@AXL-CNRB0000011-Laundary-payment";

        assertThatThrownBy(() -> upiNarrationParser.parse(narration))
                .isInstanceOf(MalformedNarrationException.class)
                .hasMessage("Missing Bank Ref");
    }
}