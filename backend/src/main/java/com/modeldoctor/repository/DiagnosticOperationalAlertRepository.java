package com.modeldoctor.repository;

import com.modeldoctor.domain.AlertLifecycleState;
import com.modeldoctor.domain.DiagnosticOperationalAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticOperationalAlertRepository extends JpaRepository<DiagnosticOperationalAlert, Long> {

    List<DiagnosticOperationalAlert> findByModelLineageIdOrderByLastObservedAtDesc(String modelLineageId);

    List<DiagnosticOperationalAlert> findByModelLineageIdAndLifecycleStateInOrderByLastObservedAtDesc(
            String modelLineageId, List<AlertLifecycleState> states);

    Optional<DiagnosticOperationalAlert> findByModelLineageIdAndAlertFingerprint(String modelLineageId, String alertFingerprint);

    List<DiagnosticOperationalAlert> findByModelLineageIdAndCurrentSeverity(String modelLineageId, String currentSeverity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM DiagnosticOperationalAlert a WHERE a.modelLineageId = :modelLineageId")
    void deleteByModelLineageId(String modelLineageId);
}
