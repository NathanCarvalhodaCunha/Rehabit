package com.rehabit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SessaoDTO {

    private Integer id;
    private LocalDate data;
    private Integer duracao;
    private BigDecimal amplitudeMedia;

    public SessaoDTO() {
    }

    public SessaoDTO(Integer id, LocalDate data, Integer duracao, BigDecimal amplitudeMedia) {
        this.id = id;
        this.data = data;
        this.duracao = duracao;
        this.amplitudeMedia = amplitudeMedia;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
}
