package io.github.harshjoeyit.core.parse.hdfc.delimited;

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

class HdfcCreditCardStatementParserTest {

    private HdfcCreditCardStatementParser parser;

    @BeforeEach
    void setUp() {
        parser = new HdfcCreditCardStatementParser();
    }

    @Test
    void parseDelimited_onMissingHeaderPrefix_throwsMalformedException() {
        String csvData = "Statement format:" +
                "\nName~|~STEVEN STRANGE" +
                "\nAddress~|~177A BLEECKER STREET NY" +
                "\n..." +
                "\nAccount Summary" +
                "\n..." +
                "\nCard No: 5191 79XX XXXX 4197" +
                "\nAAN: 0001013270009947178" +
                "\nPast Dues (if any)" +
                "\n..." +
                "\nReward Points Summary" +
                "\n..." +
                "\nRewards Program Points Summary" +
                "\n..." +
                "\nState account branch GSTN: ...";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        assertThatThrownBy(() -> parser.parseDelimited(reader))
                .isInstanceOf(MalformedCsvException.class)
                .hasMessageContaining("Invalid CSV format. Transaction table header not found.");
    }

    @Test
    void parseDelimited_onInvalidCsvHeaders_throwsMalformedException() {
        String csvData = "Statement format:" +
                "\nName~|~STEVEN STRANGE" +
                "\nAddress~|~177A BLEECKER STREET NY" +
                "\n..." +
                "\nAccount Summary" +
                "\n..." +
                "\nCard No: 5191 79XX XXXX 4197" +
                "\nAAN: 0001013270009947178" +
                "\nPast Dues (if any)" +
                "\n..." +
                "\nDomestic / International Transactions" +
                // invalid columns - Transaction time, Merchant
                "\nTransaction type~|~Primary / Addon Customer Name~|~Transaction time~|~Merchant~|~AMT~|~Debit /Credit~|~REWARDS~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~17/08/2026 08:49:36~|~Swiggy Food BENGALURU ~|~1,211.00~|~~|~~|~" +
                "\nReward Points Summary" +
                "\n..." +
                "\nRewards Program Points Summary" +
                "\n..." +
                "\nState account branch GSTN: ...";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        assertThatThrownBy(() -> parser.parseDelimited(reader))
                .isInstanceOf(MalformedCsvException.class)
                .hasMessageContaining("Unexpected CSV headers");
    }

    @Test
    void parseDelimited_onValidCSV_returnsNormalizedTxns() throws IOException {
        String csvData = "Statement format:" +
                "\nName~|~STEVEN STRANGE" +
                "\nAddress~|~177A BLEECKER STREET NY" +
                "\n..." +
                "\nAccount Summary" +
                "\n..." +
                "\nCard No: 5191 79XX XXXX 4197" +
                "\nAAN: 0001013270009947178" +
                "\nPast Dues (if any)" +
                "\n..." +
                "\nDomestic / International Transactions" +
                "\nTransaction type~|~Primary / Addon Customer Name~|~DATE~|~Description~|~AMT~|~Debit /Credit~|~REWARDS~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~17/08/2026 08:49:36~|~Swiggy Food BENGALURU ~|~1,211.00~|~~|~~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~17/08/2026 20:46:59~|~Amazon Seller Services BANGALORE ~|~205.00~|~~|~~|~" +
                "\nReward Points Summary" +
                "\n..." +
                "\nRewards Program Points Summary" +
                "\n..." +
                "\nState account branch GSTN: ...";

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
                        1211.00,
                        "Swiggy Food BENGALURU",
                        LocalDateTime.of(2026, 8, 17, 8, 49, 36),
                        TxnMode.CREDIT_CARD,
                        TxnDirection.DEBIT,
                        TxnType.PURCHASE,
                        CounterpartyType.MERCHANT,
                        null,
                        "CSVRecord [comment='null', recordNumber=1, values=[Domestic, HARSHIT GANGWAR, 17/08/2026 08:49:36, Swiggy Food BENGALURU, 1,211.00, , , ]]"
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
                        205.00,
                        "Amazon Seller Services BANGALORE",
                        LocalDateTime.of(2026, 8, 17, 20, 46, 59),
                        TxnDirection.DEBIT,
                        TxnType.PURCHASE
                );
    }

    @Test
    void parseDelimited_onRefundTransactions_returnsRefundNormalizedTxns() throws IOException {
        String csvData = "Statement format:" +
                "\nName~|~STEVEN STRANGE" +
                "\nAddress~|~177A BLEECKER STREET NY" +
                "\n..." +
                "\nDomestic / International Transactions" +
                "\nTransaction type~|~Primary / Addon Customer Name~|~DATE~|~Description~|~AMT~|~Debit /Credit~|~REWARDS~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~30/05/2026 00:00:00~|~MYNTRA DESIGNS PRIVATE Bangalore ~|~472.00~|~Cr~|~~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~30/05/2026 00:00:00~|~MYNTRA DESIGNS PRIVATE Bangalore ~|~399.00~|~Cr~|~~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~30/05/2026 00:00:00~|~MYNTRA DESIGNS PRIVATE Bangalore ~|~503.00~|~Cr~|~~|~" +
                "\nReward Points Summary" +
                "\n...";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

        assertThat(transactions).hasSize(3);

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
                        472.00,
                        "MYNTRA DESIGNS PRIVATE Bangalore",
                        LocalDateTime.of(2026, 5, 30, 0, 0, 0),
                        TxnMode.CREDIT_CARD,
                        TxnDirection.CREDIT,
                        TxnType.REFUND,
                        CounterpartyType.MERCHANT,
                        null,
                        "CSVRecord [comment='null', recordNumber=1, values=[Domestic, HARSHIT GANGWAR, 30/05/2026 00:00:00, MYNTRA DESIGNS PRIVATE Bangalore, 472.00, Cr, , ]]"
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
                        399.00,
                        "MYNTRA DESIGNS PRIVATE Bangalore",
                        LocalDateTime.of(2026, 5, 30, 0, 0, 0),
                        TxnDirection.CREDIT,
                        TxnType.REFUND
                );

        assertThat(transactions.get(2))
                .extracting(
                        NormalizedTransaction::getAmount,
                        NormalizedTransaction::getCounterparty,
                        NormalizedTransaction::getDatetime,
                        NormalizedTransaction::getDirection,
                        NormalizedTransaction::getType
                )
                .containsExactly(
                        503.00,
                        "MYNTRA DESIGNS PRIVATE Bangalore",
                        LocalDateTime.of(2026, 5, 30, 0, 0, 0),
                        TxnDirection.CREDIT,
                        TxnType.REFUND
                );
    }

    @Test
    void parseDelimited_onCreditCardBillPaymentTransactions_returnsBillPaymentNormalizedTxns() throws IOException {
        String csvData = "Statement format:" +
                "\nName~|~STEVEN STRANGE" +
                "\nAddress~|~177A BLEECKER STREET NY" +
                "\n..." +
                "\nDomestic / International Transactions" +
                "\nTransaction type~|~Primary / Addon Customer Name~|~DATE~|~Description~|~AMT~|~Debit /Credit~|~REWARDS~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~01/09/2026 15:13:41~|~BPPY CC PAYMENT PP0162 44BB2JP16AR03 (Ref# ST262450083000010118269)~|~8,415.00~|~Cr~|~~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~01/06/2026 19:04:57~|~CREDIT CARD PAYMENT Net Banking (Ref# 00000000000601019427348)~|~17,827.00~|~Cr~|~~|~" +
                "\nReward Points Summary" +
                "\n...";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

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
                        8415.00,
                        "BPPY CC PAYMENT PP0162 44BB2JP16AR03 (Ref# ST262450083000010118269)",
                        LocalDateTime.of(2026, 9, 1, 15, 13, 41),
                        TxnMode.CREDIT_CARD,
                        TxnDirection.CREDIT,
                        TxnType.CREDIT_CARD_BILL_PAYMENT,
                        CounterpartyType.BANK,
                        null,
                        "CSVRecord [comment='null', recordNumber=1, values=[Domestic, HARSHIT GANGWAR, 01/09/2026 15:13:41, BPPY CC PAYMENT PP0162 44BB2JP16AR03 (Ref# ST262450083000010118269), 8,415.00, Cr, , ]]"
                );

        assertThat(transactions.get(1))
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
                        17827.00,
                        "CREDIT CARD PAYMENT Net Banking (Ref# 00000000000601019427348)",
                        LocalDateTime.of(2026, 6, 1, 19, 4, 57),
                        TxnMode.CREDIT_CARD,
                        TxnDirection.CREDIT,
                        TxnType.CREDIT_CARD_BILL_PAYMENT,
                        CounterpartyType.BANK,
                        null,
                        "CSVRecord [comment='null', recordNumber=2, values=[Domestic, HARSHIT GANGWAR, 01/06/2026 19:04:57, CREDIT CARD PAYMENT Net Banking (Ref# 00000000000601019427348), 17,827.00, Cr, , ]]"
                );
    }

    @Test
    void parseDelimited_onZeroTransactions_returnsEmptyList() throws IOException {
        String csvData = "Statement format:" +
                "\nName~|~STEVEN STRANGE" +
                "\nAddress~|~177A BLEECKER STREET NY" +
                "\n..." +
                "\nAccount Summary" +
                "\n..." +
                "\nCard No: 5191 79XX XXXX 4197" +
                "\nAAN: 0001013270009947178" +
                "\nPast Dues (if any)" +
                "\n..." +
                "\nDomestic / International Transactions" +
                "\nTransaction type~|~Primary / Addon Customer Name~|~DATE~|~Description~|~AMT~|~Debit /Credit~|~REWARDS~|~" +
                "\nReward Points Summary" +
                "\n..." +
                "\nRewards Program Points Summary" +
                "\n..." +
                "\nState account branch GSTN: ...";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

        assertThat(transactions).isEmpty();
    }

    @Test
    void parseDelimited_onMetadataAndFooterRows_skipsNonTransactionRows() throws IOException {
        String csvData = "Statement format:" +
                "\nName~|~STEVEN STRANGE" +
                "\nAddress~|~177A BLEECKER STREET NY" +
                "\n..." +
                "\nDomestic / International Transactions" +
                "\nTransaction type~|~Primary / Addon Customer Name~|~DATE~|~Description~|~AMT~|~Debit /Credit~|~REWARDS~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~14/06/2026 00:00:00~|~Cashfree*Myntra BANGALORE ~|~2,299.00~|~Cr~|~~|~" +
                "\n" +
                "\nReward Points Summary" +
                "\nOpening Balance~|~Earned~|~Disbursed~|~Adjusted/Lapsed~|~Closing Balance~|~Points expiring in next 30 days~|~Points expiring in next 60 days" +
                "\n1,489~|~761~|~1,489~|~0~|~761~|~0~|~0" +
                "\n" +
                "\nRewards Program Points Summary" +
                "\nPrograms~|~Bonus Points" +
                "\nEARNED 5% CASHBACK_SELECT MERCHANTS~|~761" +
                "\n" +
                "\nCashback Summary" +
                "\nTransaction~|~Amount" +
                "\nCASHBACK FOR REDEMPTIO N OF PO240526~|~1,489.00" +
                "\n" +
                "\nGST Summary" +
                "\nIGST~|~CGST~|~SGST~|~Reversal~|~Total" +
                "\n9~|~0~|~0~|~0~|~9" +
                "\n" +
                "\n*GST levied on statement date is always billed in the subsequent statement." +
                "\n" +
                "\nState account branch GSTN: 33AAACH2702H2Z6 [ State code : Tamilnadu ]" +
                "\nHSN Code : 997113" +
                "\nRegistered Office Address: HDFC Bank Cards Division, Door No 94 SP, Estate Bus Stand, Wavin Main Road, Mogappair West, Chennai - 600058.";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0))
                .extracting(
                        NormalizedTransaction::getAmount,
                        NormalizedTransaction::getCounterparty,
                        NormalizedTransaction::getDatetime,
                        NormalizedTransaction::getDirection,
                        NormalizedTransaction::getType
                )
                .containsExactly(
                        2299.00,
                        "Cashfree*Myntra BANGALORE",
                        LocalDateTime.of(2026, 6, 14, 0, 0, 0),
                        TxnDirection.CREDIT,
                        TxnType.REFUND
                );
    }

    @Test
    void parseDelimited_onEmptyCsv_throwsMalformedException() {
        BufferedReader reader = new BufferedReader(new StringReader(""));

        assertThatThrownBy(() -> parser.parseDelimited(reader))
                .isInstanceOf(MalformedCsvException.class)
                .hasMessageContaining("Invalid CSV format. Transaction table header not found.");
    }

    @Test
    void parseDelimited_onMalformedRow_skipsRowAndContinues() throws IOException {
        String csvData = "Statement format:" +
                "\nName~|~STEVEN STRANGE" +
                "\nAddress~|~177A BLEECKER STREET NY" +
                "\n..." +
                "\nDomestic / International Transactions" +
                "\nTransaction type~|~Primary / Addon Customer Name~|~DATE~|~Description~|~AMT~|~Debit /Credit~|~REWARDS~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~17/08/2026 08:49:36~|~Swiggy Food BENGALURU ~|~1,211.00~|~~|~~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~INVALID_DATE~|~Corrupt Date Merchant ~|~500.00~|~~|~~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~17/08/2026 12:00:00~|~Corrupt Amount Merchant ~|~INVALID_AMT~|~~|~~|~" +
                "\nDomestic~|~HARSHIT GANGWAR ~|~17/08/2026 20:46:59~|~Amazon Seller Services BANGALORE ~|~205.00~|~~|~~|~" +
                "\nReward Points Summary" +
                "\n...";

        BufferedReader reader = new BufferedReader(new StringReader(csvData));

        List<NormalizedTransaction> transactions = parser.parseDelimited(reader);

        // Only row 1 and row 4 should be successfully parsed
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getCounterparty()).isEqualTo("Swiggy Food BENGALURU");
        assertThat(transactions.get(0).getAmount()).isEqualTo(1211.00);

        assertThat(transactions.get(1).getCounterparty()).isEqualTo("Amazon Seller Services BANGALORE");
        assertThat(transactions.get(1).getAmount()).isEqualTo(205.00);
    }
}