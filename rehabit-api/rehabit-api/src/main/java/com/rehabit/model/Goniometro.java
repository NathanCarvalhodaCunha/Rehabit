package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "tb04_goniometro")
public class Goniometro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb04_id_goniometro")
    private Integer id;

    @Column(name = "tb04_bateria")
    private Integer bateria;

    @Column(name = "tb04_data_sincronizacao")
    private LocalDate dataSincronizacao;

    @Column(name = "tb04_hora_sincronizacao")
    private LocalTime horaSincronizacao;

    @Column(name = "tb04_id_clinica", nullable = false)
    private Integer idClinica;

    public Goniometro() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getBateria() {
        return bateria;
    }

    public void setBateria(Integer bateria) {
        this.bateria = bateria;
    }

    public LocalDate getDataSincronizacao() {
        return dataSincronizacao;
    }

    public void setDataSincronizacao(LocalDate dataSincronizacao) {
        this.dataSincronizacao = dataSincronizacao;
    }

    public LocalTime getHoraSincronizacao() {
        return horaSincronizacao;
    }

    public void setHoraSincronizacao(LocalTime horaSincronizacao) {
        this.horaSincronizacao = horaSincronizacao;
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }
}
