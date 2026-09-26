package io.github.harshjoeyit.core.parse.hdfc.narration;

import io.github.harshjoeyit.core.parse.exception.MalformedNarrationException;
import io.github.harshjoeyit.core.parse.model.NarrationParseResult;
import io.github.harshjoeyit.core.parse.model.NarrationType;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class ImpsNarrationParserTest {

    private ImpsNarrationParser impsNarrationParser;

    @BeforeEach
    void setUp() {
        impsNarrationParser = new ImpsNarrationParser();
    }

    @Test
    void canParse_onNonImpsNarration_returnsFalse() {
        assertThat(impsNarrationParser.canParse("UPI-12345")).isEqualTo(false);
    }

    @Test
    void canParse_onImpsNarration_returnsFalse() {
        assertThat(impsNarrationParser.canParse("IMPS-12345")).isEqualTo(true);
    }

    @Test
    void parse_validNarrationWithUserComment_returnsParsedResults() {
        String narration = "IMPS-622725189133-HRITIJ GUPTA-BARB-XXXXXXXXXX6199-ARCHITECT";
        NarrationParseResult result = impsNarrationParser.parse(narration);

        assertThat(result.getNarrationType()).isEqualTo(NarrationType.IMPS);
        assertThat(result.getTxnMode()).isEqualTo(TxnMode.IMPS);
        assertThat(result.getCounterparty()).isEqualTo("HRITIJ GUPTA");
        assertThat(result.getCounterpartyId()).isEqualTo("HRITIJ GUPTA-BARB-XXXXXXXXXX6199");
        assertThat(result.getCounterpartyType()).isNull();
        assertThat(result.getBankReference()).isEqualTo("622725189133");
        assertThat(result.getUserComment()).isEqualTo("ARCHITECT");
    }

    @Test
    void parse_validNarrationWithoutUserComment_returnsParsedResults() {
        String narration = "IMPS-622725189133-HRITIJ GUPTA-BARB-XXXXXXXXXX6199";
        NarrationParseResult result = impsNarrationParser.parse(narration);

        assertThat(result.getNarrationType()).isEqualTo(NarrationType.IMPS);
        assertThat(result.getTxnMode()).isEqualTo(TxnMode.IMPS);
        assertThat(result.getCounterparty()).isEqualTo("HRITIJ GUPTA");
        assertThat(result.getCounterpartyId()).isEqualTo("HRITIJ GUPTA-BARB-XXXXXXXXXX6199");
        assertThat(result.getCounterpartyType()).isNull();
        assertThat(result.getBankReference()).isEqualTo("622725189133");
        assertThat(result.getUserComment()).isNull();
    }

    @Test
    void parse_validNarrationWithInvalidNarration_throwsMalformedException() {
        String narration = "IMPS-HRITIJ GUPTA-BARB-XXXXXXXXXX6199";

        assertThatThrownBy(() -> impsNarrationParser.parse(narration))
                .isInstanceOf(MalformedNarrationException.class)
                .hasMessage("Invalid narration");
    }
}