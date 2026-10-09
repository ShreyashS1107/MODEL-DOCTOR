package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticIncidentAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticIncidentAlertRepository extends JpaRepository<DiagnosticIncidentAlert, Long> {

    List<DiagnosticIncidentAlert> findByIncidentId(Long incidentId);

    List<DiagnosticIncidentAlert> findByAlertId(Long alertId);

    Optional<DiagnosticIncidentAlert> findByIncidentIdAndAlertId(Long incidentId, Long alertId);

    void deleteByIncidentId(Long incidentId);
}
