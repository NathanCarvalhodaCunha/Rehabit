package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Código de 6 dígitos que a clínica mostra na tela e a pessoa digita no
 * portal do goniômetro. Fica em tabela, e não em memória, porque o serviço
 * reinicia com frequência no plano gratuito — perder o código no meio da
 * configuração seria justamente o pior momento.
 */
@Entity
@Table(name = "tb10_pareamento")
public class CodigoPareamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb10_id_pareamento")
    private Integer id;

    @Column(name = "tb10_codigo", nullable = false, length = 6)
    private String codigo;

    @Column(name = "tb10_id_clinica", nullable = false)
    private Integer idClinica;

    @Column(name = "tb10_expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "tb10_usado", columnDefinition = "boolean not null default false")
    private boolean usado;

    public CodigoPareamento() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(LocalDateTime expiraEm) {
        this.expiraEm = expiraEm;
    }

    public boolean isUsado() {
        return usado;
    }

    public void setUsado(boolean usado) {
        this.usado = usado;
    }
}
