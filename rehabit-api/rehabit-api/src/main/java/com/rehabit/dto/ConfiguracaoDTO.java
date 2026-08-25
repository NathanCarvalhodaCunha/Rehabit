package com.rehabit.dto;

import java.time.LocalTime;

public class ConfiguracaoDTO {

    private LocalTime horaAbertura;
    private LocalTime horaFechamento;
    private Integer duracaoPadraoMin;
    private boolean avisarConflito = true;

    public ConfiguracaoDTO() {
    }

    public ConfiguracaoDTO(LocalTime horaAbertura, LocalTime horaFechamento,
                            Integer duracaoPadraoMin, boolean avisarConflito) {
        this.horaAbertura = horaAbertura;
        this.horaFechamento = horaFechamento;
        this.duracaoPadraoMin = duracaoPadraoMin;
        this.avisarConflito = avisarConflito;
    }

    public LocalTime getHoraAbertura() {
        return horaAbertura;
    }

    public void setHoraAbertura(LocalTime horaAbertura) {
        this.horaAbertura = horaAbertura;
    }

    public LocalTime getHoraFechamento() {
        return horaFechamento;
    }

    public void setHoraFechamento(LocalTime horaFechamento) {
        this.horaFechamento = horaFechamento;
    }

    public Integer getDuracaoPadraoMin() {
        return duracaoPadraoMin;
    }

    public void setDuracaoPadraoMin(Integer duracaoPadraoMin) {
        this.duracaoPadraoMin = duracaoPadraoMin;
    }

    public boolean isAvisarConflito() {
        return avisarConflito;
    }

    public void setAvisarConflito(boolean avisarConflito) {
        this.avisarConflito = avisarConflito;
    }
}
