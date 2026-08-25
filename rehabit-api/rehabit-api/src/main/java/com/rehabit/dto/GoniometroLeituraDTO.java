package com.rehabit.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GoniometroLeituraDTO {

    @NotNull(message = "A clínica é obrigatória.")
    private Integer idClinica;

    @NotNull(message = "O ângulo é obrigatório.")
    private BigDecimal angulo;

    public GoniometroLeituraDTO() {
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }

    public BigDecimal getAngulo() {
        return angulo;
    }

    public void setAngulo(BigDecimal angulo) {
        this.angulo = angulo;
    }
}
