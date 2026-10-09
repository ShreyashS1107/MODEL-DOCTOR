package com.modeldoctor.dto;

public class StartValidationRequestDto {

    private String actor = "USER";
    private String experimentId;
    private String validationNotes;

    public StartValidationRequestDto() {}

    public StartValidationRequestDto(String actor, String experimentId, String validationNotes) {
        this.actor = actor;
        this.experimentId = experimentId;
        this.validationNotes = validationNotes;
    }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { this.experimentId = experimentId; }

    public String getValidationNotes() { return validationNotes; }
    public void setValidationNotes(String validationNotes) { this.validationNotes = validationNotes; }
}
