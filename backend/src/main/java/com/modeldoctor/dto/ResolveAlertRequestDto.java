package com.modeldoctor.dto;

public class ResolveAlertRequestDto {

    private String reason;
    private String actor;

    public ResolveAlertRequestDto() {}

    public ResolveAlertRequestDto(String reason, String actor) {
        this.reason = reason;
        this.actor = actor;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
}
