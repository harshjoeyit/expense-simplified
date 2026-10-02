package io.github.harshjoeyit.core.parse.scapia.delimited;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.parse.exception.MalformedCsvException;
import io.github.harshjoeyit.core.parse.model.CounterpartyType;
import io.github.harshjoeyit.core.parse.model.TxnDirection;
import io.github.harshjoeyit.core.parse.model.TxnMode;
import io.github.harshjoeyit.core.parse.model.TxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScapiaCreditCardStatementParserTest {

    private ScapiaCreditCardStatementParser parser;

    @BeforeEach
    void setUp() {
        this.parser = new ScapiaCreditCardStatementParser();
    }

    @Test
    void parseDelimited_onInvalidCsvHeaders_throwsMalformedException() throws IOException {
        String csvData = "date,time,merchant,amt"
                + "\n25-07-2026,21:23,Google Play Refund,2499.00"
                + "\n16-07-2026,10:50,Sameera P,-24.00";
        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        assertThatThrownBy(() -> parser.parseDelimited(reader))
                .isInstanceOf(MalformedCsvException.class)
                .hasMessageContaining("Unexpected CSV headers:");
    }

    @Test
    void parseDelimited_onValidCSV_returnsNormalizedTxns() throws IOException {
        // Arrange
        String headerFormat = "date,time,description,amount";
        String csvData = headerFormat
                + "\n25-07-2026,21:23,Google Play Refund,2499.00"
                + "\n16-07-2026,10:50,Sameera P,-24.00";
        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        // Act
        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

        // Assert
        assertThat(transactions).hasSize(2);

        assertThat(transactions.get(0))
                .extracting(
                        NormalizedTransaction::getAmount,
                        NormalizedTransaction::getCounterparty,
                        NormalizedTransaction::getDatetime,
                        NormalizedTransaction::getMode,
                        NormalizedTransaction::getDirection,
                        NormalizedTransaction::getType,
                        NormalizedTransaction::getCounterpartyType,
                        NormalizedTransaction::getBankReference,
                        NormalizedTransaction::getRawTxn

                )
                .containsExactly(
                        2499.00,
                        "Google Play Refund",
                        LocalDateTime.of(2026, 7, 25, 21, 23),
                        TxnMode.CREDIT_CARD,
                        TxnDirection.CREDIT,
                        TxnType.REFUND,
                        CounterpartyType.MERCHANT,
                        null,
                        "CSVRecord [comment='null', recordNumber=1, values=[25-07-2026, 21:23, Google Play Refund, 2499.00]]"
                    );

        assertThat(transactions.get(1))
                .extracting(
                        NormalizedTransaction::getAmount,
                        NormalizedTransaction::getCounterparty,
                        NormalizedTransaction::getDatetime,
                        NormalizedTransaction::getDirection,
                        NormalizedTransaction::getType
                )
                .containsExactly(
                        24.00,
                        "Sameera P",
                        LocalDateTime.of(2026, 7, 16, 10, 50),
                        TxnDirection.DEBIT,
                        TxnType.PURCHASE
                );
    }

    @Test
    void parseDelimited_onMalformedRow_skipsRowAndContinues() throws IOException {
        String headerFormat = "date,time,description,amount";
        String csvData = headerFormat
                + "\n25-07-2026,21:23,Google Play Refund,2499.00"
                + "\n25-07-2026,INVALID_TIME,Invalid Time Merchant,50.00"
                + "\nINVALID_DATE,12:00,Invalid Date Merchant,100.00"
                + "\n16-07-2026,10:50,Invalid Amount Merchant,INVALID_AMT"
                + "\n16-07-2026,10:50,Sameera P,-24.00";
        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

        // Only row 1 and row 5 should be successfully parsed
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getCounterparty()).isEqualTo("Google Play Refund");
        assertThat(transactions.get(0).getAmount()).isEqualTo(2499.00);

        assertThat(transactions.get(1).getCounterparty()).isEqualTo("Sameera P");
        assertThat(transactions.get(1).getAmount()).isEqualTo(24.00);
    }
}