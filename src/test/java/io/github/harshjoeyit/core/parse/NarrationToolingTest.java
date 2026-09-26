package io.github.harshjoeyit.core.parse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NarrationToolingTest {

    private NarrationTooling narrationTooling;
    private String validNarration = "UPI-HAXXX G-HARXXXXAAR02@SBI-CNRB0000011-287713541080-PAYMENT FROM PHONE";

    @BeforeEach
    void setUp() {
        narrationTooling = new NarrationTooling();
    }

    @Test
    void extract12DigUpiUtr_validPayload_returnsTwelveDigitUtr() {
        String result = narrationTooling.extract12DigUpiUtr(validNarration);
        assertThat(result).hasSize(12).matches("\\d+").isEqualTo("287713541080");
    }

    @Test
    void extract12DigUpiUtr_utrNotFound_returnsNull() {
        String text = "Hello";
        String result = narrationTooling.extract12DigUpiUtr(text);
        assertThat(result).isNull();
    }

    @Test
    void extract12DigUpiUtr_blankPayload_returnsNull() {
        String text = "";
        String result = narrationTooling.extract12DigUpiUtr(text);
        assertThat(result).isNull();
    }

    @Test
    void extractBankIfsc_validPayload_returnsIfsc() {
        String result = narrationTooling.extractBankIfsc(validNarration);
        assertThat(result).hasSize(11).isEqualTo("CNRB0000011");
    }

    @Test
    void extractBankIfsc_longerThanRequireLengthIfscPayload_returnsNull() {
        String text = "UPI-ABC-123@SBI-CNRB0123456789-287713541080";
        String result = narrationTooling.extractBankIfsc(text);
        assertThat(result).isNull();
    }

    @Test
    void extractBankIfsc_shorterThanRequireLengthIfscPayload_returnsNull() {
        String text = "UPI-ABC-123@SBI-CNRB0123-287713541080";
        String result = narrationTooling.extractBankIfsc(text);
        assertThat(result).isNull();
    }

    @Test
    void extractUpiId_validPayload_returnsPrefixUpiId() {
        String result = narrationTooling.extractPrefixUpiId("HARXXXXAAR02@SBI-CNRB0000011");
        assertThat(result).isEqualTo("HARXXXXAAR02@SBI");
    }

    @Test
    void extractUpiId_validPayloadWithHyphen_returnsPrefixUpiId() {
        String result = narrationTooling.extractPrefixUpiId("HARSH-1@OKHDFC-CNRB0000011");
        assertThat(result).isEqualTo("HARSH-1@OKHDFC");
    }
}