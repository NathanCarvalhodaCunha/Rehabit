package com.rehabit.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "tb06_medicao")
public class Medicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb06_id_medicao")
    private Integer id;

    @Column(name = "tb06_amplitude_media", precision = 6, scale = 2)
    private BigDecimal amplitudeMedia;

    @Column(name = "tb06_data_medicao")
    private LocalDate dataMedicao;

    @Column(name = "tb06_hora_medicao")
    private LocalTime horaMedicao;

    @Column(name = "tb06_id_sessoes", nullable = false)
    private Integer idSessao;

    public Medicao() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public BigDecimal getAmplitudeMedia() {
        return amplitudeMedia;
    }

    public void setAmplitudeMedia(BigDecimal amplitudeMedia) {
        this.amplitudeMedia = amplitudeMedia;
    }

    public LocalDate getDataMedicao() {
        return dataMedicao;
    }

    public void setDataMedicao(LocalDate dataMedicao) {
        this.dataMedicao = dataMedicao;
    }

    public LocalTime getHoraMedicao() {
        return horaMedicao;
    }

    public void setHoraMedicao(LocalTime horaMedicao) {
        this.horaMedicao = horaMedicao;
    }

    public Integer getIdSessao() {
        return idSessao;
    }

    public void setIdSessao(Integer idSessao) {
        this.idSessao = idSessao;
    }
}
