package com.rehabit.dto;

import java.time.LocalDateTime;

/** O que a tela mostra depois de pedir um código novo. */
public class CodigoPareamentoDTO {

    private String codigo;
    private LocalDateTime expiraEm;
    private long validadeSegundos;

    public CodigoPareamentoDTO() {
    }

    public CodigoPareamentoDTO(String codigo, LocalDateTime expiraEm, long validadeSegundos) {
        this.codigo = codigo;
        this.expiraEm = expiraEm;
        this.validadeSegundos = validadeSegundos;
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime expiraEm) { this.expiraEm = expiraEm; }
    public long getValidadeSegundos() { return validadeSegundos; }
    public void setValidadeSegundos(long validadeSegundos) { this.validadeSegundos = validadeSegundos; }
}
