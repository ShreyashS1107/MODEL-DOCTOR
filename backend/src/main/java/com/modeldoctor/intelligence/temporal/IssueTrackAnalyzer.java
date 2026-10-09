package com.modeldoctor.intelligence.temporal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticTemporalObservation;
import com.modeldoctor.dto.IssueTrackDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@SuppressWarnings("null")
public class IssueTrackAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(IssueTrackAnalyzer.class);
    private final PersistenceAnalyzer persistenceAnalyzer;
    private final ObjectMapper objectMapper;

    public IssueTrackAnalyzer(PersistenceAnalyzer persistenceAnalyzer, ObjectMapper objectMapper) {
        this.persistenceAnalyzer = persistenceAnalyzer;
        this.objectMapper = objectMapper;
    }

    public List<IssueTrackDto> buildIssueTracks(String modelLineageId, List<DiagnosticTemporalObservation> observations) {
        List<IssueTrackDto> tracks = new ArrayList<>();
        if (observations == null || observations.isEmpty()) {
            return tracks;
        }

        // Group observations by targetKey
        Map<String, List<DiagnosticTemporalObservation>> targetGroups = new LinkedHashMap<>();
        for (DiagnosticTemporalObservation obs : observations) {
            String key = obs.getTargetKey();
            if (key == null || key.isBlank() || key.equalsIgnoreCase("GLOBAL") || key.equalsIgnoreCase("NONE")) {
                continue;
            }
            targetGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(obs);
        }

        for (Map.Entry<String, List<DiagnosticTemporalObservation>> entry : targetGroups.entrySet()) {
            String targetKey = entry.getKey();
            List<DiagnosticTemporalObservation> obsList = entry.getValue();

            // Sort chronologically
            obsList.sort(Comparator.comparing(DiagnosticTemporalObservation::getTimestamp));

            DiagnosticTemporalObservation first = obsList.get(0);
            DiagnosticTemporalObservation last = obsList.get(obsList.size() - 1);

            Set<String> modules = new LinkedHashSet<>();
            Set<String> metricNames = new LinkedHashSet<>();
            Set<String> runIds = new LinkedHashSet<>();

            // Group observations by runId while preserving chronological order
            Map<String, List<DiagnosticTemporalObservation>> runObsMap = new LinkedHashMap<>();
            for (DiagnosticTemporalObservation o : obsList) {
                runObsMap.computeIfAbsent(o.getRunId(), k -> new ArrayList<>()).add(o);
                if (o.getModule() != null) modules.add(o.getModule().name());
                if (o.getMetricName() != null) metricNames.add(o.getMetricName());
                if (o.getRunId() != null) runIds.add(o.getRunId());
            }

            List<String> severitySequence = new ArrayList<>();
            List<Map<String, Object>> stateHistory = new ArrayList<>();

            for (Map.Entry<String, List<DiagnosticTemporalObservation>> runEntry : runObsMap.entrySet()) {
                String rId = runEntry.getKey();
                List<DiagnosticTemporalObservation> rObs = runEntry.getValue();
                Instant rTime = rObs.get(0).getTimestamp();

                String highestSev = "LOW";
                int highestRank = 0;
                for (DiagnosticTemporalObservation o : rObs) {
                    String s = (o.getSeverity() != null && !o.getSeverity().isBlank()) ? o.getSeverity() : "LOW";
                    int r = persistenceAnalyzer.severityRank(s);
                    if (r >= highestRank) {
                        highestRank = r;
                        highestSev = s;
                    }
                }
                severitySequence.add(highestSev);

                Map<String, Object> point = new HashMap<>();
                point.put("runId", rId);
                point.put("timestamp", rTime.toString());
                point.put("severity", highestSev);
                point.put("observationCount", rObs.size());
                stateHistory.add(point);
            }

            PersistenceAnalyzer.PersistenceResult pRes = persistenceAnalyzer.evaluatePersistence(severitySequence);

            IssueTrackDto track = new IssueTrackDto();
            track.setModelLineageId(modelLineageId);
            track.setTrackFingerprint(targetKey);
            track.setTargetType(first.getTargetType() != null ? first.getTargetType() : "FEATURE");
            track.setTargetKey(targetKey);
            track.setFirstSeenAt(first.getTimestamp());
            track.setLastSeenAt(last.getTimestamp());
            track.setFirstSeenRunId(first.getRunId());
            track.setLastSeenRunId(last.getRunId());
            track.setObservationCount(obsList.size());
            track.setConsecutiveCount(pRes.consecutiveCount);
            track.setCurrentSeverity(pRes.currentSeverity);
            track.setPeakSeverity(pRes.peakSeverity);
            track.setStatus(pRes.status);
            track.setModulesInvolved(new ArrayList<>(modules));
            track.setMetricNames(new ArrayList<>(metricNames));
            track.setRunIds(new ArrayList<>(runIds));

            try {
                track.setHistoryJson(objectMapper.writeValueAsString(stateHistory));
            } catch (Exception e) {
                logger.warn("Failed to serialize historyJson for track {}: {}", targetKey, e.getMessage());
            }

            tracks.add(track);
        }

        // Sort tracks by currentSeverity DESC, peakSeverity DESC, observationCount DESC
        tracks.sort((a, b) -> {
            int rA = persistenceAnalyzer.severityRank(a.getCurrentSeverity());
            int rB = persistenceAnalyzer.severityRank(b.getCurrentSeverity());
            if (rA != rB) return Integer.compare(rB, rA);
            int pA = persistenceAnalyzer.severityRank(a.getPeakSeverity());
            int pB = persistenceAnalyzer.severityRank(b.getPeakSeverity());
            if (pA != pB) return Integer.compare(pB, pA);
            return Integer.compare(b.getObservationCount(), a.getObservationCount());
        });

        return tracks;
    }
}
