package io.github.harshjoeyit.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NormalizedTransactionTest {

    @Test
    void shouldDefaultMyShareToAmountWhenConstructedWithoutMyShare() {
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(250.75)
                .build();

        assertEquals(250.75, txn.getMyShare());
        org.junit.jupiter.api.Assertions.assertTrue(txn.toString().contains("myShare=250.75"));
    }

    @Test
    void shouldKeepExplicitMyShareWhenProvided() {
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(250.75)
                .myShare(100.0)
                .build();

        assertEquals(100.0, txn.getMyShare());
    }

    @Test
    void shouldHandleNullAmountGracefully() {
        NormalizedTransaction txn = NormalizedTransaction.builder().build();

        assertNull(txn.getAmount());
        assertNull(txn.getMyShare());
    }

    @Test
    void shouldPreserveMyShareAcrossToBuilder() {
        NormalizedTransaction txn = NormalizedTransaction.builder()
                .amount(300.0)
                .build();

        assertEquals(300.0, txn.getMyShare());

        NormalizedTransaction modified = txn.toBuilder()
                .counterparty("Store")
                .build();

        assertEquals(300.0, modified.getMyShare());
        assertEquals("Store", modified.getCounterparty());
    }
}
