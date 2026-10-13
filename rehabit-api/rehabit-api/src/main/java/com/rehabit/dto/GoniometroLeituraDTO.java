package com.rehabit.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GoniometroLeituraDTO {

    /**
     * Obrigatório só quando quem envia é a clínica logada. Um goniômetro
     * pareado NÃO manda este campo de propósito: a clínica sai do token dele,
     * então um aparelho não consegue gravar leitura na clínica de outro.
     * A exigência para o caminho da clínica está no GoniometroController.
     */
    private Integer idClinica;

    @NotNull(message = "O ângulo é obrigatório.")
    @Digits(integer = 3, fraction = 6, message = "Ângulo em formato inválido.")
    @DecimalMin(value = "-5", message = "Ângulo fora da faixa do goniômetro (0° a 180°).")
    @DecimalMax(value = "185", message = "Ângulo fora da faixa do goniômetro (0° a 180°).")
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
