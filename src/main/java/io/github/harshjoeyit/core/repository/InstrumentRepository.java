package io.github.harshjoeyit.core.repository;

import io.github.harshjoeyit.core.model.Instrument;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository {
    Optional<Instrument> findById(String id);
    List<Instrument> findByUserId(String userId);
    void saveInstrument(Instrument instrument);
}
