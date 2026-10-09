package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticMonitoringPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiagnosticMonitoringPolicyRepository extends JpaRepository<DiagnosticMonitoringPolicy, Long> {

    Optional<DiagnosticMonitoringPolicy> findByModelLineageId(String modelLineageId);

    boolean existsByModelLineageId(String modelLineageId);
}
