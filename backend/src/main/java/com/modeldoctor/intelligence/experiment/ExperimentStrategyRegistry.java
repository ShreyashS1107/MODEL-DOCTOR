package com.modeldoctor.intelligence.experiment;

import com.modeldoctor.domain.ExperimentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ExperimentStrategyRegistry {

    private final List<DiagnosticExperimentStrategy> strategies;

    public ExperimentStrategyRegistry(List<DiagnosticExperimentStrategy> strategies) {
        this.strategies = strategies;
    }

    public Optional<DiagnosticExperimentStrategy> getStrategy(ExperimentType type) {
        if (type == null) return Optional.empty();
        return strategies.stream()
                .filter(s -> s.supports(type))
                .findFirst();
    }

    public List<DiagnosticExperimentStrategy> getAllStrategies() {
        return strategies;
    }
}
