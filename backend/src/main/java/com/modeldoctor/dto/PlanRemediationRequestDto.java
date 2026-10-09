package com.modeldoctor.dto;

public class PlanRemediationRequestDto {

    private String actor = "USER";
    private Long remediationId;
    private String planDetails;

    public PlanRemediationRequestDto() {}

    public PlanRemediationRequestDto(String actor, Long remediationId, String planDetails) {
        this.actor = actor;
        this.remediationId = remediationId;
        this.planDetails = planDetails;
    }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public Long getRemediationId() { return remediationId; }
    public void setRemediationId(Long remediationId) { this.remediationId = remediationId; }

    public String getPlanDetails() { return planDetails; }
    public void setPlanDetails(String planDetails) { this.planDetails = planDetails; }
}
