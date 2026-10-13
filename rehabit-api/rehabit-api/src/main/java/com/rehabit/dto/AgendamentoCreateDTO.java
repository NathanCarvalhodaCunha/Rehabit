package com.rehabit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public class AgendamentoCreateDTO {

    @NotNull(message = "O paciente é obrigatório.")
    private Integer idPaciente;

    @NotNull(message = "A data é obrigatória.")
    private LocalDate data;

    @NotNull(message = "O horário é obrigatório.")
    private LocalTime hora;

    @Size(max = 255, message = "A observação pode ter no máximo 255 caracteres.")
    private String observacao;

    public AgendamentoCreateDTO() {
    }

    public Integer getIdPaciente() {
        return idPaciente;
    }

    public void setIdPaciente(Integer idPaciente) {
        this.idPaciente = idPaciente;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}
