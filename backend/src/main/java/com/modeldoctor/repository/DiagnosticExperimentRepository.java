package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticExperiment;
import com.modeldoctor.domain.ExperimentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticExperimentRepository extends JpaRepository<DiagnosticExperiment, String> {

    List<DiagnosticExperiment> findByBaselineRunIdOrderByCreatedAtDesc(String baselineRunId);

    List<DiagnosticExperiment> findByBaselineRunIdIn(List<String> baselineRunIds);

    List<DiagnosticExperiment> findByBaselineRunIdAndStatus(String baselineRunId, ExperimentStatus status);

    Optional<DiagnosticExperiment> findByIdAndBaselineRunId(String id, String baselineRunId);

    @Modifying
    @Query("DELETE FROM DiagnosticExperiment e WHERE e.baselineRunId = :baselineRunId")
    void deleteByBaselineRunId(@Param("baselineRunId") String baselineRunId);
}
