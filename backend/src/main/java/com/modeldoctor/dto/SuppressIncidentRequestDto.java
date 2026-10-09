package com.modeldoctor.dto;

public class SuppressIncidentRequestDto {

    private String actor = "USER";
    private String reason = "Temporarily suppressed by operator";
    private int durationHours = 24;

    public SuppressIncidentRequestDto() {}

    public SuppressIncidentRequestDto(String actor, String reason, int durationHours) {
        this.actor = actor;
        this.reason = reason;
        this.durationHours = durationHours;
    }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getDurationHours() { return durationHours; }
    public void setDurationHours(int durationHours) { this.durationHours = durationHours; }
}
