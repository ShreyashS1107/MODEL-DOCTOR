package com.modeldoctor.dto;

public class AcknowledgeAlertRequestDto {

    private String actor;

    public AcknowledgeAlertRequestDto() {}

    public AcknowledgeAlertRequestDto(String actor) {
        this.actor = actor;
    }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
}
