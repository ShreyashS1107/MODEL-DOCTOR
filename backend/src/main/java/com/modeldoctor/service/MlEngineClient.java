package com.modeldoctor.service;

import com.modeldoctor.dto.MlEngineJobRequestDto;
import com.modeldoctor.dto.MlEngineJobResponseDto;
import com.modeldoctor.exception.DiagnosticExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Service
public class MlEngineClient {

    private static final Logger log = LoggerFactory.getLogger(MlEngineClient.class);

    private final RestTemplate restTemplate;
    private final String mlEngineBaseUrl;

    public MlEngineClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${modeldoctor.ml-engine.base-url:http://localhost:8000}") String mlEngineBaseUrl,
            @Value("${modeldoctor.ml-engine.timeout-ms:15000}") int timeoutMs) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.mlEngineBaseUrl = mlEngineBaseUrl;
    }

    /**
     * Dispatches a diagnostic job to the Python ML Engine.
     */
    public MlEngineJobResponseDto executeJob(MlEngineJobRequestDto jobRequest) {
        String endpoint = mlEngineBaseUrl + "/api/v1/diagnostics/run";
        log.info("Dispatching diagnostic job {} to Python ML Engine at {}", jobRequest.getRunId(), endpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<MlEngineJobRequestDto> entity = new HttpEntity<>(jobRequest, headers);

        try {
            ResponseEntity<MlEngineJobResponseDto> response = restTemplate.postForEntity(
                    endpoint,
                    entity,
                    MlEngineJobResponseDto.class
            );

            if (response.getBody() == null) {
                throw new DiagnosticExecutionException("Received empty response from Python ML Engine for run " + jobRequest.getRunId());
            }

            log.info("Successfully received diagnostic execution response for run {}. Status: {}",
                    jobRequest.getRunId(), response.getBody().getStatus());
            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            String errorMsg = String.format("ML Engine returned HTTP %d: %s", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            log.error("Failed to execute ML engine job {}: {}", jobRequest.getRunId(), errorMsg, ex);
            throw new DiagnosticExecutionException(errorMsg, ex);

        } catch (RestClientException ex) {
            String errorMsg = String.format("Unable to communicate with Python ML Engine at %s: %s", endpoint, ex.getMessage());
            log.error("ML Engine connection error for run {}: {}", jobRequest.getRunId(), errorMsg, ex);
            throw new DiagnosticExecutionException(errorMsg, ex);
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> applyIntervention(java.util.Map<String, Object> request) {
        String endpoint = mlEngineBaseUrl + "/api/v1/experiments/apply-intervention";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<java.util.Map> response = restTemplate.postForEntity(endpoint, entity, java.util.Map.class);
            return response.getBody();
        } catch (Exception ex) {
            log.error("Failed to apply intervention via ML Engine: {}", ex.getMessage());
            throw new DiagnosticExecutionException("Failed to apply intervention: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> computeStatisticalComparison(java.util.Map<String, Object> request) {
        String endpoint = mlEngineBaseUrl + "/api/v1/experiments/statistical-comparison";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<java.util.Map> response = restTemplate.postForEntity(endpoint, entity, java.util.Map.class);
            return response.getBody();
        } catch (Exception ex) {
            log.error("Failed to compute statistical comparison via ML Engine: {}", ex.getMessage());
            throw new DiagnosticExecutionException("Failed to compute statistical comparison: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> evaluateThresholdCounterfactual(java.util.Map<String, Object> request) {
        String endpoint = mlEngineBaseUrl + "/api/v1/experiments/threshold-counterfactual";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<java.util.Map> response = restTemplate.postForEntity(endpoint, entity, java.util.Map.class);
            return response.getBody();
        } catch (Exception ex) {
            log.error("Failed to evaluate threshold counterfactual via ML Engine: {}", ex.getMessage());
            throw new DiagnosticExecutionException("Failed to evaluate threshold counterfactual: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> evaluateCalibrationCounterfactual(java.util.Map<String, Object> request) {
        String endpoint = mlEngineBaseUrl + "/api/v1/experiments/calibration-counterfactual";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<java.util.Map> response = restTemplate.postForEntity(endpoint, entity, java.util.Map.class);
            return response.getBody();
        } catch (Exception ex) {
            log.error("Failed to evaluate calibration counterfactual via ML Engine: {}", ex.getMessage());
            throw new DiagnosticExecutionException("Failed to evaluate calibration counterfactual: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> evaluateSubgroupCounterfactual(java.util.Map<String, Object> request) {
        String endpoint = mlEngineBaseUrl + "/api/v1/experiments/subgroup-counterfactual";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<java.util.Map> response = restTemplate.postForEntity(endpoint, entity, java.util.Map.class);
            return response.getBody();
        } catch (Exception ex) {
            log.error("Failed to evaluate subgroup counterfactual via ML Engine: {}", ex.getMessage());
            throw new DiagnosticExecutionException("Failed to evaluate subgroup counterfactual: " + ex.getMessage(), ex);
        }
    }
}
