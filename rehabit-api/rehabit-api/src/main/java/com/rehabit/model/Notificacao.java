package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb07_notificacao")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb07_id_notificacao")
    private Integer id;

    @Column(name = "tb07_id_clinica", nullable = false)
    private Integer idClinica;

    @Column(name = "tb07_tipo", nullable = false)
    private String tipo;

    @Column(name = "tb07_mensagem", nullable = false, length = 500)
    private String mensagem;

    @Column(name = "tb07_lida", nullable = false)
    private boolean lida;

    @Column(name = "tb07_criada_em", nullable = false)
    private LocalDateTime criadaEm;

    public Notificacao() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
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
