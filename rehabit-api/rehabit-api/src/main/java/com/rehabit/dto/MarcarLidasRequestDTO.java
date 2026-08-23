package com.rehabit.dto;

import jakarta.validation.constraints.NotNull;

public class MarcarLidasRequestDTO {

    @NotNull(message = "A clínica é obrigatória.")
    private Integer idClinica;

    public MarcarLidasRequestDTO() {
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }
}
