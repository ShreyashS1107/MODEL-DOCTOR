package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticFleetPattern;
import com.modeldoctor.domain.FleetPatternType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticFleetPatternRepository extends JpaRepository<DiagnosticFleetPattern, Long> {

    Optional<DiagnosticFleetPattern> findByPatternKey(String patternKey);

    List<DiagnosticFleetPattern> findAllByOrderByAffectedLineagesCountDesc();

    List<DiagnosticFleetPattern> findByPatternTypeOrderByAffectedLineagesCountDesc(FleetPatternType patternType);
}
