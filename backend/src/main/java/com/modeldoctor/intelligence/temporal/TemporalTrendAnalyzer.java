package com.modeldoctor.intelligence.temporal;

import com.modeldoctor.dto.TemporalMetricHistoryDto;
import com.modeldoctor.dto.TemporalMetricPointDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TemporalTrendAnalyzer {

    public void analyzeTrend(TemporalMetricHistoryDto history) {
        List<TemporalMetricPointDto> points = history.getBaselinePoints();
        if (points == null || points.isEmpty()) {
            history.setObservationCount(0);
            history.setTrendDirection("INSUFFICIENT_DATA");
            return;
        }

        int n = points.size();
        history.setObservationCount(n);

        // Basic stats
        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double[] values = new double[n];

        for (int i = 0; i < n; i++) {
            double v = points.get(i).getValue() != null ? points.get(i).getValue() : 0.0;
            values[i] = v;
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }

        double mean = sum / n;
        history.setMean(round(mean, 4));
        history.setMinimum(round(min, 4));
        history.setMaximum(round(max, 4));

        // Standard deviation & median
        double varSum = 0.0;
        for (double v : values) {
            varSum += Math.pow(v - mean, 2);
        }
        double std = Math.sqrt(varSum / Math.max(1, n));
        history.setStandardDeviation(round(std, 4));
        history.setCoefficientOfVariation(Math.abs(mean) > 1e-6 ? round(std / Math.abs(mean), 4) : 0.0);

        // Sort copy for median
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        double median = (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        history.setMedian(round(median, 4));

        // Latest & previous delta
        double latest = values[n - 1];
        history.setLatestValue(round(latest, 4));
        if (n > 1) {
            double prev = values[n - 2];
            history.setPreviousValue(round(prev, 4));
            double absDelta = latest - prev;
            history.setAbsoluteDelta(round(absDelta, 4));
            history.setRelativeDelta(Math.abs(prev) > 1e-6 ? round(absDelta / Math.abs(prev), 4) : 0.0);
        } else {
            history.setPreviousValue(round(latest, 4));
            history.setAbsoluteDelta(0.0);
            history.setRelativeDelta(0.0);
        }

        // Rolling baseline (first half or up to first 5 observations)
        int baselineWindow = Math.max(1, Math.min(5, n / 2));
        double baseSum = 0.0;
        for (int i = 0; i < baselineWindow; i++) {
            baseSum += values[i];
        }
        double bMean = baseSum / baselineWindow;
        double bVar = 0.0;
        for (int i = 0; i < baselineWindow; i++) {
            bVar += Math.pow(values[i] - bMean, 2);
        }
        double bStd = Math.sqrt(bVar / Math.max(1, baselineWindow));
        history.setBaselineMean(round(bMean, 4));
        history.setBaselineStd(round(bStd, 4));
        if (bStd > 1e-6) {
            history.setStandardizedDeviation(round((latest - bMean) / bStd, 4));
        } else {
            history.setStandardizedDeviation(0.0);
        }

        if (n < 3) {
            history.setTrendDirection("INSUFFICIENT_DATA");
            history.setSlope(0.0);
            history.setrSquared(0.0);
            return;
        }

        // Linear slope: x = [0, 1, ..., n-1]
        double xMean = (n - 1) / 2.0;
        double num = 0.0;
        double den = 0.0;
        for (int i = 0; i < n; i++) {
            num += (i - xMean) * (values[i] - mean);
            den += Math.pow(i - xMean, 2);
        }

        double slope = den > 0 ? num / den : 0.0;
        history.setSlope(round(slope, 6));

        // R²
        double ssTot = 0.0;
        double ssRes = 0.0;
        for (int i = 0; i < n; i++) {
            double pred = mean + slope * (i - xMean);
            ssRes += Math.pow(values[i] - pred, 2);
            ssTot += Math.pow(values[i] - mean, 2);
        }
        double r2 = ssTot > 0 ? Math.max(0.0, 1.0 - (ssRes / ssTot)) : 0.0;
        history.setrSquared(round(r2, 4));

        // Direction
        boolean higherIsBetter = history.isHigherIsBetter();
        double cv = history.getCoefficientOfVariation();

        if (cv > 0.35 && r2 < 0.20 && (max - min) > 0.10) {
            history.setTrendDirection("VOLATILE");
        } else if (higherIsBetter) {
            if (slope > 0.005) {
                history.setTrendDirection("IMPROVING");
            } else if (slope < -0.005) {
                history.setTrendDirection("DEGRADING");
            } else {
                history.setTrendDirection("STABLE");
            }
        } else {
            if (slope < -0.005) {
                history.setTrendDirection("IMPROVING");
            } else if (slope > 0.005) {
                history.setTrendDirection("DEGRADING");
            } else {
                history.setTrendDirection("STABLE");
            }
        }

        // Mann-Kendall trend test if n >= 5
        if (n >= 5) {
            int s = 0;
            for (int k = 0; k < n - 1; k++) {
                for (int j = k + 1; j < n; j++) {
                    double diff = values[j] - values[k];
                    if (diff > 1e-9) s++;
                    else if (diff < -1e-9) s--;
                }
            }
            double tau = (2.0 * s) / (n * (n - 1.0));
            history.setMannKendallTau(round(tau, 4));

            double varS = (n * (n - 1.0) * (2 * n + 5.0)) / 18.0;
            double z = 0.0;
            if (s > 0) z = (s - 1.0) / Math.sqrt(varS);
            else if (s < 0) z = (s + 1.0) / Math.sqrt(varS);

            // Approx p-value
            double pVal = 2.0 * (1.0 - standardNormalCdf(Math.abs(z)));
            history.setMannKendallPValue(round(pVal, 5));
            history.setMannKendallSignificant(pVal < 0.05);
        }
    }

    private double standardNormalCdf(double z) {
        // Error function approximation
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }

    private double erf(double x) {
        // Abramowitz and Stegun approximation
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;

        int sign = 1;
        if (x < 0) sign = -1;
        x = Math.abs(x);

        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);

        return sign * y;
    }

    private double round(double val, int decimals) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return 0.0;
        double p = Math.pow(10, decimals);
        return Math.round(val * p) / p;
    }
}
