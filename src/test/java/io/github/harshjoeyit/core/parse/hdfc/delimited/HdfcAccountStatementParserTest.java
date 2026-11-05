package io.github.harshjoeyit.core.parse.hdfc.delimited;

import io.github.harshjoeyit.core.model.NormalizedTransaction;
import io.github.harshjoeyit.core.model.TxnReviewStatus;
import io.github.harshjoeyit.core.parse.NarrationTooling;
import io.github.harshjoeyit.core.parse.exception.MalformedCsvException;
import io.github.harshjoeyit.core.parse.hdfc.narration.ImpsNarrationParser;
import io.github.harshjoeyit.core.parse.hdfc.narration.UpiNarrationParser;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdfcAccountStatementParserTest {

    private HdfcAccountStatementParser parser;

    @BeforeEach
    void setUp() {
        UpiNarrationParser upiParser = new UpiNarrationParser(new NarrationTooling());
        ImpsNarrationParser impsParser = new ImpsNarrationParser();
        parser = new HdfcAccountStatementParser(Set.of(upiParser, impsParser));
    }

    @Test
    void parseDelimited_onInvalidHeaders_throwsMalformedException() {
        String csvData = "Date,Narration,Value Dat,Amount\n01/09/26,UPI-TEST,01/09/26,100.00";
        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        assertThatThrownBy(() -> parser.parseDelimited(reader))
                .isInstanceOf(MalformedCsvException.class)
                .hasMessageContaining("Unexpected CSV headers");
    }

    @Test
    void parseDelimited_onValidCsv_returnsNormalizedTransactions() throws IOException {
        String csvData = "  Date     ,Narration                                                                                                                ,Value Dat,Debit Amount       ,Credit Amount      ,Chq/Ref Number   ,Closing Balance\n" +
                " 01/09/26  ,UPI-MD SAHIL AHMED-6901101156@PTYES-SBIN0009144-128824703732-AIRPORT                                                     ,01/09/26 ,        861.00     ,          0.00     ,0000128824703732       ,    206691.81  \n" +
                " 02/09/26  ,UPI-HARSHIT GANGWAR-HARSHITGANGWAR02@YBL-BARB0VJMNRE-128849238164-CARDS                                                  ,02/09/26 ,          0.00     ,      30000.00     ,0000128849238164       ,    176691.81  ";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

        assertThat(transactions).hasSize(2);

        // Check first transaction (debit, with userComment, counterpartyId, bankReference, valueDatetime, rawTxn)
        assertThat(transactions.get(0))
                .extracting(
                        NormalizedTransaction::getAmount,
                        NormalizedTransaction::getDirection,
                        NormalizedTransaction::getType,
                        NormalizedTransaction::getMode,
                        NormalizedTransaction::getDatetime,
                        NormalizedTransaction::getValueDatetime,
                        NormalizedTransaction::getCounterparty,
                        NormalizedTransaction::getCounterpartyId,
                        NormalizedTransaction::getCounterpartyType,
                        NormalizedTransaction::getUserComment,
                        NormalizedTransaction::getBankReference,
                        NormalizedTransaction::getReviewStatus,
                        NormalizedTransaction::getRawTxn
                )
                .containsExactly(
                        861.00,
                        TxnDirection.DEBIT,
                        null,
                        TxnMode.UPI,
                        LocalDateTime.of(2026, 9, 1, 0, 0),
                        LocalDateTime.of(2026, 9, 1, 0, 0),
                        "MD SAHIL AHMED",
                        "6901101156@PTYES",
                        null,
                        "AIRPORT",
                        "128824703732",
                        TxnReviewStatus.PENDING,
                        "CSVRecord [comment='null', recordNumber=1, values=[01/09/26, UPI-MD SAHIL AHMED-6901101156@PTYES-SBIN0009144-128824703732-AIRPORT, 01/09/26, 861.00, 0.00, 0000128824703732, 206691.81]]"
                );

        // Check second transaction (credit)
        assertThat(transactions.get(1))
                .extracting(
                        NormalizedTransaction::getAmount,
                        NormalizedTransaction::getDirection,
                        NormalizedTransaction::getType,
                        NormalizedTransaction::getMode,
                        NormalizedTransaction::getDatetime,
                        NormalizedTransaction::getValueDatetime,
                        NormalizedTransaction::getCounterparty,
                        NormalizedTransaction::getCounterpartyId,
                        NormalizedTransaction::getCounterpartyType,
                        NormalizedTransaction::getUserComment,
                        NormalizedTransaction::getBankReference,
                        NormalizedTransaction::getReviewStatus
                )
                .containsExactly(
                        30000.00,
                        TxnDirection.CREDIT,
                        null,
                        TxnMode.UPI,
                        LocalDateTime.of(2026, 9, 2, 0, 0),
                        LocalDateTime.of(2026, 9, 2, 0, 0),
                        "HARSHIT GANGWAR",
                        "HARSHITGANGWAR02@YBL",
                        null,
                        "CARDS",
                        "128849238164",
                        TxnReviewStatus.PENDING
                );
    }

    @Test
    void parseDelimited_onMalformedRow_skipsRowAndContinues() throws IOException {
        String csvData = "Date,Narration,Value Dat,Debit Amount,Credit Amount,Chq/Ref Number,Closing Balance\n" +
                // Row 1: Valid
                "01/09/26,UPI-MD SAHIL AHMED-6901101156@PTYES-SBIN0009144-128824703732-AIRPORT,01/09/26,861.00,0.00,0000128824703732,206691.81\n" +
                // Row 2: Malformed amount (non-numeric debit amount)
                "01/09/26,SOME_TXN_NARRATION,01/09/26,NOT_A_NUMBER,0.00,000012345678,206191.81\n" +
                // Row 3: Malformed date
                "INVALID_DATE,UPI-HARSHIT GANGWAR-HARSHITGANGWAR02@YBL-BARB0VJMNRE-128849238164-CARDS,02/09/26,100.00,0.00,0000128849238164,206091.81\n" +
                // Row 4: Valid
                "03/09/26,UPI-ARPIT KUMAR PARMAR-8871448182@YBL-BARB0VJMNRE-624652532327-RENT ETC,03/09/26,13833.00,0.00,0000624652532327,162858.81";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

        // Only row 1 and row 4 should be successfully parsed
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getCounterparty()).isEqualTo("MD SAHIL AHMED");
        assertThat(transactions.get(0).getAmount()).isEqualTo(861.00);

        assertThat(transactions.get(1).getCounterparty()).isEqualTo("ARPIT KUMAR PARMAR");
        assertThat(transactions.get(1).getAmount()).isEqualTo(13833.00);
    }

    @Test
    void parseDelimited_onNonUpiNonImpsNarration_usesFallbackNarrationAndSupportsFourDigitYear() throws IOException {
        String csvData = "Date,Narration,Value Dat,Debit Amount,Credit Amount,Chq/Ref Number,Closing Balance\n" +
                "01/09/2026,POS 4012XXXXXXXX1234 SWIGGY BANGALORE,01/09/2026,450.00,0.00,REF12345,10000.00\n" +
                "02/09/26,SWEEP-IN CREDIT - 5030129999,02/09/26,0.00,5000.00,000000000000000,15000.00\n" +
                "03/09/26,NEFT CR-HDFC0000123-COMPANY-SALARY,03/09/26,0.00,50000.00,REF99999,65000.00";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

        assertThat(transactions).hasSize(3);

        // Transaction 1: POS Debit Card with 4-digit year
        NormalizedTransaction posTxn = transactions.get(0);
        assertThat(posTxn.getAmount()).isEqualTo(450.00);
        assertThat(posTxn.getDirection()).isEqualTo(TxnDirection.DEBIT);
        assertThat(posTxn.getDatetime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(posTxn.getCounterparty()).isEqualTo("POS 4012XXXXXXXX1234 SWIGGY BANGALORE");
        assertThat(posTxn.getBankReference()).isEqualTo("REF12345");
        assertThat(posTxn.getReviewStatus()).isEqualTo(TxnReviewStatus.PENDING);

        // Transaction 2: Sweep In
        NormalizedTransaction sweepTxn = transactions.get(1);
        assertThat(sweepTxn.getAmount()).isEqualTo(5000.00);
        assertThat(sweepTxn.getDirection()).isEqualTo(TxnDirection.CREDIT);
        assertThat(sweepTxn.getCounterparty()).isEqualTo("SWEEP-IN CREDIT - 5030129999");
        assertThat(sweepTxn.getMode()).isEqualTo(null);
        assertThat(sweepTxn.getBankReference()).isEqualTo("000000000000000");

        // Transaction 3: NEFT
        NormalizedTransaction neftTxn = transactions.get(2);
        assertThat(neftTxn.getAmount()).isEqualTo(50000.00);
        assertThat(neftTxn.getDirection()).isEqualTo(TxnDirection.CREDIT);
        assertThat(neftTxn.getCounterparty()).isEqualTo("NEFT CR-HDFC0000123-COMPANY-SALARY");
        assertThat(neftTxn.getMode()).isEqualTo(TxnMode.FT);
        assertThat(neftTxn.getBankReference()).isEqualTo("REF99999");
    }

    @Test
    void parseDelimited_withLeadingMetadataRowsAndUppercaseHeaders_parsesSuccessfully() throws IOException {
        String csvData = "HDFC BANK STATEMENT\n" +
                "Account No: 50100012345678\n" +
                "Statement Period: 01/08/2026 to 31/08/2026\n" +
                "\n" +
                "DATE,NARRATION,VALUE DATE,DEBIT AMOUNT,CREDIT AMOUNT,CHQ/REF NUMBER,CLOSING BALANCE\n" +
                "01-08-2026,POS 4012XXXXXXXX1234 SWIGGY,01-08-2026,350.00,0.00,REF111,50000.00\n" +
                "02-08-26,UPI-FRIEND-FRIEND@OKAXIS-SBIN0001234-123456789012-FOOD,02-08-26,0.00,1000.00,000123456789012,51000.00";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getAmount()).isEqualTo(350.00);
        assertThat(transactions.get(0).getCounterparty()).isEqualTo("POS 4012XXXXXXXX1234 SWIGGY");
        assertThat(transactions.get(0).getDatetime()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));

        assertThat(transactions.get(1).getAmount()).isEqualTo(1000.00);
        assertThat(transactions.get(1).getCounterparty()).isEqualTo("FRIEND");
        assertThat(transactions.get(1).getDatetime()).isEqualTo(LocalDateTime.of(2026, 8, 2, 0, 0));
    }
}
