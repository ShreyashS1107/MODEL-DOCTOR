package com.modeldoctor.dto;

public class SuppressAlertRequestDto {

    private String reason;
    private Integer durationHours; // default e.g. 24
    private String actor;

    public SuppressAlertRequestDto() {}

    public SuppressAlertRequestDto(String reason, Integer durationHours, String actor) {
        this.reason = reason;
        this.durationHours = durationHours;
        this.actor = actor;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getDurationHours() { return durationHours; }
    public void setDurationHours(Integer durationHours) { this.durationHours = durationHours; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
}
