package com.rehabit.dto;

import java.math.BigDecimal;

public class GoniometroLeituraRespostaDTO {

    private BigDecimal angulo;

    public GoniometroLeituraRespostaDTO() {
    }

    public GoniometroLeituraRespostaDTO(BigDecimal angulo) {
        this.angulo = angulo;
    }

    public BigDecimal getAngulo() {
        return angulo;
    }

    public void setAngulo(BigDecimal angulo) {
        this.angulo = angulo;
    }
}
