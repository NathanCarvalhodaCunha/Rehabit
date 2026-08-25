package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "tb05_sessoes")
public class Sessao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb05_id_sessoes")
    private Integer id;

    @Column(name = "tb05_duracao")
    private Integer duracao;

    @Column(name = "tb05_data_sessoes")
    private LocalDate dataSessao;

    @Column(name = "tb05_hora_sessoes")
    private LocalTime horaSessao;

    @Column(name = "tb05_id_fisioterapeuta", nullable = false)
    private Integer idFisioterapeuta;

    @Column(name = "tb05_id_paciente", nullable = false)
    private Integer idPaciente;

    /** Prontuário da sessão: o que foi trabalhado, evolução observada, conduta. */
    @Column(name = "tb05_observacoes", columnDefinition = "TEXT")
    private String observacoes;

    public Sessao() {
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDuracao() {
        return duracao;
    }

    public void setDuracao(Integer duracao) {
        this.duracao = duracao;
    }

    public LocalDate getDataSessao() {
        return dataSessao;
    }

    public void setDataSessao(LocalDate dataSessao) {
        this.dataSessao = dataSessao;
    }

    public LocalTime getHoraSessao() {
        return horaSessao;
    }

    public void setHoraSessao(LocalTime horaSessao) {
        this.horaSessao = horaSessao;
    }

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }

    public Integer getIdPaciente() {
        return idPaciente;
    }

    public void setIdPaciente(Integer idPaciente) {
        this.idPaciente = idPaciente;
    }
}
