package io.github.harshjoeyit.service;

import io.github.harshjoeyit.cli.command.StatementType;
import io.github.harshjoeyit.core.model.Instrument;
import io.github.harshjoeyit.core.repository.InstrumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    public Optional<StatementType> getStatementType(String instrumentId) {
        return instrumentRepository
                .findById(instrumentId)
                .map(Instrument::statementType);
    }
}
