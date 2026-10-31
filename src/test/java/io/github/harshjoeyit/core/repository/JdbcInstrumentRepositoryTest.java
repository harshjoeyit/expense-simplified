package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.cli.command.StatementType;
import io.github.harshjoeyit.core.model.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.persistence=sqlite",
        "spring.shell.interactive.enabled=false",
        "spring.datasource.url=jdbc:sqlite:target/test-expense-simplified.db?journal_mode=WAL&foreign_keys=ON"
})
@TestExecutionListeners(listeners = {
        DependencyInjectionTestExecutionListener.class
})
class JdbcInstrumentRepositoryTest {

    @Autowired
    private JdbcInstrumentRepository instrumentRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM instruments").update();
    }

    @Test
    void saveAndFindById_returnsPersistedInstrument() {
        Instrument instrument = Instrument.builder()
                .id("test-acc")
                .description("Test HDFC Account")
                .maskedNum("XXX9999")
                .statementType(StatementType.HDFC_ACC)
                .userId("user-1")
                .build();

        instrumentRepository.saveInstrument(instrument);

        Optional<Instrument> found = instrumentRepository.findById("test-acc");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("test-acc");
        assertThat(found.get().description()).isEqualTo("Test HDFC Account");
        assertThat(found.get().maskedNum()).isEqualTo("XXX9999");
        assertThat(found.get().statementType()).isEqualTo(StatementType.HDFC_ACC);
        assertThat(found.get().userId()).isEqualTo("user-1");
    }

    @Test
    void saveInstrument_updatesExistingOnConflict() {
        Instrument instrument = Instrument.builder()
                .id("test-cc")
                .description("Old Card Name")
                .maskedNum("XXX1111")
                .statementType(StatementType.HDFC_CC)
                .build();

        instrumentRepository.saveInstrument(instrument);

        Instrument updated = Instrument.builder()
                .id("test-cc")
                .description("New Card Name")
                .maskedNum("XXX2222")
                .statementType(StatementType.HDFC_CC)
                .build();

        instrumentRepository.saveInstrument(updated);

        Optional<Instrument> found = instrumentRepository.findById("test-cc");
        assertThat(found).isPresent();
        assertThat(found.get().description()).isEqualTo("New Card Name");
        assertThat(found.get().maskedNum()).isEqualTo("XXX2222");
    }

    @Test
    void findByUserId_filtersCorrectly() {
        Instrument user1Inst = Instrument.builder()
                .id("user1-inst")
                .description("User 1 Inst")
                .statementType(StatementType.HDFC_ACC)
                .userId("user-1")
                .build();

        Instrument user2Inst = Instrument.builder()
                .id("user2-inst")
                .description("User 2 Inst")
                .statementType(StatementType.SCAPIA_CC)
                .userId("user-2")
                .build();

        instrumentRepository.saveInstrument(user1Inst);
        instrumentRepository.saveInstrument(user2Inst);

        List<Instrument> user1List = instrumentRepository.findByUserId("user-1");
        assertThat(user1List).hasSize(1);
        assertThat(user1List.get(0).id()).isEqualTo("user1-inst");

        List<Instrument> all = instrumentRepository.findByUserId(null);
        assertThat(all).hasSize(2);
    }

    @Test
    void init_doesNotOverwriteExistingDatabaseRecords() {
        // Pre-insert an instrument that exists in instruments.yml with a customized description
        Instrument customized = Instrument.builder()
                .id("hdfc-sal-acc")
                .description("Custom User Description")
                .maskedNum("XXX1234")
                .statementType(StatementType.HDFC_ACC)
                .build();

        instrumentRepository.saveInstrument(customized);

        // Run init() again (simulating subsequent app startups)
        instrumentRepository.init();

        Optional<Instrument> found = instrumentRepository.findById("hdfc-sal-acc");
        assertThat(found).isPresent();
        assertThat(found.get().description()).isEqualTo("Custom User Description");
    }
}
