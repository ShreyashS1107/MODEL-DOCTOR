package com.modeldoctor.dto;

public class AcknowledgeIncidentRequestDto {

    private String actor = "USER";
    private String note;

    public AcknowledgeIncidentRequestDto() {}

    public AcknowledgeIncidentRequestDto(String actor, String note) {
        this.actor = actor;
        this.note = note;
    }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
