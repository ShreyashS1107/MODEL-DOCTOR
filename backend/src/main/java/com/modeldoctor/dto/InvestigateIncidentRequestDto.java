package com.modeldoctor.dto;

public class InvestigateIncidentRequestDto {

    private String actor = "USER";
    private String note;

    public InvestigateIncidentRequestDto() {}

    public InvestigateIncidentRequestDto(String actor, String note) {
        this.actor = actor;
        this.note = note;
    }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
