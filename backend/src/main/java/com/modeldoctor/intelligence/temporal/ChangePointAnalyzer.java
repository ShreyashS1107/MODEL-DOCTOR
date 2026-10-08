package com.modeldoctor.intelligence.temporal;

import com.modeldoctor.dto.ChangePointDto;
import com.modeldoctor.dto.TemporalMetricHistoryDto;
import com.modeldoctor.dto.TemporalMetricPointDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChangePointAnalyzer {

    public List<ChangePointDto> detectChangePoints(String modelLineageId, TemporalMetricHistoryDto history) {
        List<ChangePointDto> results = new ArrayList<>();
        List<TemporalMetricPointDto> points = history.getBaselinePoints();

        if (points == null || points.size() < 4) {
            return results;
        }

        int n = points.size();
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            values[i] = points.get(i).getValue() != null ? points.get(i).getValue() : 0.0;
        }

        int minSplit = 2;
        double maxScore = 0.0;
        int bestK = -1;
        double thresholdShift = 0.05;

        for (int k = minSplit; k <= n - minSplit; k++) {
            double sumBefore = 0.0;
            for (int i = 0; i < k; i++) sumBefore += values[i];
            double meanBefore = sumBefore / k;

            double sumAfter = 0.0;
            for (int i = k; i < n; i++) sumAfter += values[i];
            double meanAfter = sumAfter / (n - k);

            double absShift = Math.abs(meanAfter - meanBefore);
            double weight = Math.sqrt((k * (n - k)) / (double) n);
            double score = absShift * weight;

            if (absShift >= thresholdShift && score > maxScore) {
                maxScore = score;
                bestK = k;
            }
        }

        if (bestK != -1) {
            double sumBefore = 0.0;
            for (int i = 0; i < bestK; i++) sumBefore += values[i];
            double meanBefore = sumBefore / bestK;

            double sumAfter = 0.0;
            for (int i = bestK; i < n; i++) sumAfter += values[i];
            double meanAfter = sumAfter / (n - bestK);

            double absShift = meanAfter - meanBefore;
            double relShift = Math.abs(meanBefore) > 1e-6 ? absShift / Math.abs(meanBefore) : 0.0;

            TemporalMetricPointDto pt = points.get(bestK);

            ChangePointDto cp = new ChangePointDto();
            cp.setModelLineageId(modelLineageId);
            cp.setMetricName(history.getMetricName());
            cp.setTargetKey(history.getTargetKey());
            cp.setChangeRunId(pt.getRunId());
            cp.setChangeTimestamp(pt.getTimestamp());
            cp.setBeforeMean(round(meanBefore, 4));
            cp.setAfterMean(round(meanAfter, 4));
            cp.setAbsoluteShift(round(absShift, 4));
            cp.setRelativeShift(round(relShift, 4));

            String conf = Math.abs(absShift) >= 0.15 ? "HIGH" : (Math.abs(absShift) >= 0.08 ? "MEDIUM" : "LOW");
            cp.setConfidenceLevel(conf);

            for (int i = 0; i < bestK; i++) {
                cp.getRunIdsBefore().add(points.get(i).getRunId());
            }
            for (int i = bestK; i < n; i++) {
                cp.getRunIdsAfter().add(points.get(i).getRunId());
            }

            results.add(cp);
        }

        return results;
    }

    private double round(double val, int decimals) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return 0.0;
        double p = Math.pow(10, decimals);
        return Math.round(val * p) / p;
    }
}
