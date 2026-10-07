package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticModule;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class RetryModulesRequestDto {

    @NotEmpty(message = "At least one module must be specified for module-level retry")
    private List<DiagnosticModule> modules;

    public RetryModulesRequestDto() {}

    public RetryModulesRequestDto(List<DiagnosticModule> modules) {
        this.modules = modules;
    }

    public List<DiagnosticModule> getModules() { return modules; }
    public void setModules(List<DiagnosticModule> modules) { this.modules = modules; }
}
