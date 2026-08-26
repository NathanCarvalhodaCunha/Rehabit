package com.rehabit.dto;

import java.math.BigDecimal;

/**
 * Resultado de uma captura de amplitude: o profissional aperta "iniciar",
 * pede o movimento completo da articulação, aperta "parar" — e o mínimo, o
 * máximo e a amplitude (máx - mín) saem daqui direto para o formulário de
 * sessão.
 */
public class GoniometroCapturaDTO {

    private boolean ativa;
    private Long iniciadaEm;
    private Long duracaoSegundos;
    private Integer amostras;
    private BigDecimal minimo;
    private BigDecimal maximo;
    private BigDecimal amplitude;
    private BigDecimal media;

    public GoniometroCapturaDTO() {
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }

    public Long getIniciadaEm() {
        return iniciadaEm;
    }

    public void setIniciadaEm(Long iniciadaEm) {
        this.iniciadaEm = iniciadaEm;
    }

    public Long getDuracaoSegundos() {
        return duracaoSegundos;
    }

    public void setDuracaoSegundos(Long duracaoSegundos) {
        this.duracaoSegundos = duracaoSegundos;
    }

    public Integer getAmostras() {
        return amostras;
    }

    public void setAmostras(Integer amostras) {
        this.amostras = amostras;
    }

    public BigDecimal getMinimo() {
        return minimo;
    }

    public void setMinimo(BigDecimal minimo) {
        this.minimo = minimo;
    }

    public BigDecimal getMaximo() {
        return maximo;
    }

    public void setMaximo(BigDecimal maximo) {
        this.maximo = maximo;
    }

    public BigDecimal getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(BigDecimal amplitude) {
        this.amplitude = amplitude;
    }

    public BigDecimal getMedia() {
        return media;
    }

    public void setMedia(BigDecimal media) {
        this.media = media;
    }
}
