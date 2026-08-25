package com.rehabit.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public class AgendamentoDTO {

    private Integer id;
    private LocalDate data;
    private LocalTime hora;
    private String observacao;
    private Integer idPaciente;
    private String nomePaciente;
    private String nomeFisioterapeuta;

    public AgendamentoDTO() {
    }

    public AgendamentoDTO(Integer id, LocalDate data, LocalTime hora, String observacao,
                           Integer idPaciente, String nomePaciente) {
        this(id, data, hora, observacao, idPaciente, nomePaciente, null);
    }

    public AgendamentoDTO(Integer id, LocalDate data, LocalTime hora, String observacao,
                           Integer idPaciente, String nomePaciente, String nomeFisioterapeuta) {
        this.id = id;
        this.data = data;
        this.hora = hora;
        this.observacao = observacao;
        this.idPaciente = idPaciente;
        this.nomePaciente = nomePaciente;
        this.nomeFisioterapeuta = nomeFisioterapeuta;
    }

    public String getNomeFisioterapeuta() {
        return nomeFisioterapeuta;
    }

    public void setNomeFisioterapeuta(String nomeFisioterapeuta) {
        this.nomeFisioterapeuta = nomeFisioterapeuta;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public Integer getIdPaciente() {
        return idPaciente;
    }

    public void setIdPaciente(Integer idPaciente) {
        this.idPaciente = idPaciente;
    }

    public String getNomePaciente() {
        return nomePaciente;
    }

    public void setNomePaciente(String nomePaciente) {
        this.nomePaciente = nomePaciente;
    }
}
