package io.github.harshjoeyit.config;

import io.github.harshjoeyit.core.model.Instrument;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties
public record InstrumentConfig (
        List<Instrument> instruments
){}
