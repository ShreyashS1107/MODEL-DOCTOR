package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticModelReliability;
import com.modeldoctor.domain.ModelReliabilityState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticModelReliabilityRepository extends JpaRepository<DiagnosticModelReliability, Long> {

    Optional<DiagnosticModelReliability> findByModelLineageId(String modelLineageId);

    List<DiagnosticModelReliability> findAllByOrderByReliabilityScoreDesc();

    List<DiagnosticModelReliability> findByReliabilityState(ModelReliabilityState state);

    void deleteByModelLineageId(String modelLineageId);
}
