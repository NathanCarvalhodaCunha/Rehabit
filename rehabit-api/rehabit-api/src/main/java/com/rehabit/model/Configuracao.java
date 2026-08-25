package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalTime;

/**
 * Preferências de uma conta. Fica numa tabela própria, chaveada por
 * (tipo, id do usuário), em vez de virar colunas repetidas em
 * tb01_clinica e tb02_fisioterapeuta.
 */
@Entity
@Table(
        name = "tb08_configuracao",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tb08_tipo_usuario", "tb08_id_usuario"})
)
public class Configuracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb08_id_configuracao")
    private Integer id;

    @Column(name = "tb08_tipo_usuario", nullable = false, length = 20)
    private String tipoUsuario;

    @Column(name = "tb08_id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "tb08_hora_abertura")
    private LocalTime horaAbertura;

    @Column(name = "tb08_hora_fechamento")
    private LocalTime horaFechamento;

    @Column(name = "tb08_duracao_padrao_min")
    private Integer duracaoPadraoMin;

    @Column(name = "tb08_avisar_conflito", columnDefinition = "boolean not null default true")
    private boolean avisarConflito = true;

    public Configuracao() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTipoUsuario() {
        return tipoUsuario;
    }

    public void setTipoUsuario(String tipoUsuario) {
        this.tipoUsuario = tipoUsuario;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
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
