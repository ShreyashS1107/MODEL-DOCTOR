package com.modeldoctor.dto;

public class ResolveIncidentRequestDto {

    private String actor = "USER";
    private String resolutionReason = "Resolved by engineer";

    public ResolveIncidentRequestDto() {}

    public ResolveIncidentRequestDto(String actor, String resolutionReason) {
        this.actor = actor;
        this.resolutionReason = resolutionReason;
    }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getResolutionReason() { return resolutionReason; }
    public void setResolutionReason(String resolutionReason) { this.resolutionReason = resolutionReason; }
}
