package com.rehabit.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SessaoCreateDTO {

    @NotNull(message = "A data é obrigatória.")
    private LocalDate data;

    // Sem faixa, o prontuário aceitava duração negativa e amplitude
    // impossível (-500 min, 9999°). Além de sair assim no histórico, isso
    // contaminava a média de ganho do profissional na tela de desempenho.
    @NotNull(message = "A duração é obrigatória.")
    @Min(value = 1, message = "A duração deve ser de ao menos 1 minuto.")
    @Max(value = 600, message = "A duração não pode passar de 600 minutos.")
    private Integer duracao;

    // O goniômetro mede de 0° (braço ao lado do tronco) a ~180° (acima da
    // cabeça); o teto de 360° é folga, só para barrar o que é impossível.
    @DecimalMin(value = "0.0", message = "A amplitude não pode ser negativa.")
    @DecimalMax(value = "360.0", message = "A amplitude não pode passar de 360°.")
    private BigDecimal amplitudeMedia;

    private String observacoes;

    /** Escala de dor 0–10 relatada pelo paciente. */
    private Integer dor;

    /**
     * Instante em que começou a captura do goniômetro que preencheu a
     * amplitude, quando veio de uma. Serve de identificador: o servidor só
     * anexa a curva se ela ainda for a mesma captura.
     */
    private Long capturaIniciadaEm;

    public Long getCapturaIniciadaEm() {
        return capturaIniciadaEm;
    }

    public void setCapturaIniciadaEm(Long capturaIniciadaEm) {
        this.capturaIniciadaEm = capturaIniciadaEm;
    }

    public Integer getDor() {
        return dor;
    }

    public void setDor(Integer dor) {
        this.dor = dor;
    }

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

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }
}
