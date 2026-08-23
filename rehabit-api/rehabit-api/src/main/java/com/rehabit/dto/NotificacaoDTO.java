package com.rehabit.dto;

import java.time.LocalDateTime;

public class NotificacaoDTO {

    private Integer id;
    private String tipo;
    private String mensagem;
    private boolean lida;
    private LocalDateTime criadaEm;

    public NotificacaoDTO() {
    }

    public NotificacaoDTO(Integer id, String tipo, String mensagem, boolean lida, LocalDateTime criadaEm) {
        this.id = id;
        this.tipo = tipo;
        this.mensagem = mensagem;
        this.lida = lida;
        this.criadaEm = criadaEm;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public boolean isLida() {
        return lida;
    }

    public void setLida(boolean lida) {
        this.lida = lida;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public void setCriadaEm(LocalDateTime criadaEm) {
        this.criadaEm = criadaEm;
    }
}
