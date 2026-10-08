package com.modeldoctor.repository;

import com.modeldoctor.domain.DiagnosticIssueTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticIssueTrackRepository extends JpaRepository<DiagnosticIssueTrack, Long> {

    List<DiagnosticIssueTrack> findByModelLineageIdOrderByLastSeenAtDesc(String modelLineageId);

    Optional<DiagnosticIssueTrack> findByModelLineageIdAndTrackFingerprint(String modelLineageId, String trackFingerprint);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM DiagnosticIssueTrack t WHERE t.modelLineageId = :modelLineageId")
    void deleteByModelLineageId(String modelLineageId);
}
