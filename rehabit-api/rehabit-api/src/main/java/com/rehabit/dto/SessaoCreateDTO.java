package com.rehabit.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SessaoCreateDTO {

    @NotNull(message = "A data é obrigatória.")
    private LocalDate data;

    @NotNull(message = "A duração é obrigatória.")
    private Integer duracao;

    private BigDecimal amplitudeMedia;

    @NotNull(message = "O fisioterapeuta responsável é obrigatório.")
    private Integer idFisioterapeuta;

    public SessaoCreateDTO() {
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public Integer getDuracao() {
        return duracao;
    }

    public void setDuracao(Integer duracao) {
        this.duracao = duracao;
    }

    public BigDecimal getAmplitudeMedia() {
        return amplitudeMedia;
    }

    public void setAmplitudeMedia(BigDecimal amplitudeMedia) {
        this.amplitudeMedia = amplitudeMedia;
    }

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }
}
