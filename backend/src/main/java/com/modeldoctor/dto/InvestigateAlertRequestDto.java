package com.modeldoctor.dto;

public class InvestigateAlertRequestDto {

    private String actor;

    public InvestigateAlertRequestDto() {}

    public InvestigateAlertRequestDto(String actor) {
        this.actor = actor;
    }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
}
