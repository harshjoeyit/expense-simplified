package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.config.InstrumentConfig;
import io.github.harshjoeyit.core.model.Instrument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
@ConditionalOnProperty(name = "app.persistence", havingValue = "in-memory")
public class InMemoryInstrumentRepository implements InstrumentRepository {

    private final Map<String, Instrument> instruments = new ConcurrentHashMap<>();

    public InMemoryInstrumentRepository(InstrumentConfig instrumentConfig) {
        if (instrumentConfig.instruments() != null) {
            instrumentConfig.instruments().forEach(instrument -> {
                if (instrument.id() != null) {
                    instruments.put(instrument.id(), instrument);
                }
            });
            log.info("Loaded {} instruments, {}", instruments.size(), instruments.values());
        }
    }

    @Override
    public Optional<Instrument> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(instruments.get(id));
    }

    @Override
    public List<Instrument> findByUserId(String userId) {
        // Todo: implement when user domain established
        return new ArrayList<>(instruments.values());
    }

    @Override
    public void saveInstrument(Instrument instrument) {
        if (instrument != null && instrument.id() != null) {
            instruments.put(instrument.id(), instrument);
        }
    }
}
