package com.rehabit.dto;

import jakarta.validation.constraints.NotNull;

public class GoniometroSincronizarDTO {

    @NotNull(message = "A clínica é obrigatória.")
    private Integer idClinica;

    public GoniometroSincronizarDTO() {
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }
}
