package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "tb07_agendamento")
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb07_id_agendamento")
    private Integer id;

    @Column(name = "tb07_data_agendamento", nullable = false)
    private LocalDate dataAgendamento;

    @Column(name = "tb07_hora_agendamento", nullable = false)
    private LocalTime horaAgendamento;

    @Column(name = "tb07_observacao", length = 255)
    private String observacao;

    @Column(name = "tb07_id_fisioterapeuta", nullable = false)
    private Integer idFisioterapeuta;

    @Column(name = "tb07_id_paciente", nullable = false)
    private Integer idPaciente;

    /** AGENDADA (padrão), REALIZADA, FALTOU ou REMARCADA. */
    @Column(name = "tb07_status", length = 20)
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Agendamento() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getDataAgendamento() {
        return dataAgendamento;
    }

    public void setDataAgendamento(LocalDate dataAgendamento) {
        this.dataAgendamento = dataAgendamento;
    }

    public LocalTime getHoraAgendamento() {
        return horaAgendamento;
    }

    public void setHoraAgendamento(LocalTime horaAgendamento) {
        this.horaAgendamento = horaAgendamento;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
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
