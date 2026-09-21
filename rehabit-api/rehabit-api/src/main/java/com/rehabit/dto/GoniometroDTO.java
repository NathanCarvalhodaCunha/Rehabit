package com.rehabit.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public class GoniometroDTO {

    private Integer id;
    private Integer bateria;
    private LocalDate dataSincronizacao;
    private LocalTime horaSincronizacao;
    private Integer idClinica;
    private String numeroSerie;
    private String firmware;
    private Integer rssi;

    public GoniometroDTO() {
    }

    public GoniometroDTO(Integer id, Integer bateria, LocalDate dataSincronizacao, LocalTime horaSincronizacao,
                          Integer idClinica) {
        this.id = id;
        this.bateria = bateria;
        this.dataSincronizacao = dataSincronizacao;
        this.horaSincronizacao = horaSincronizacao;
        this.idClinica = idClinica;
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

    public String getNumeroSerie() {
        return numeroSerie;
    }

    public void setNumeroSerie(String numeroSerie) {
        this.numeroSerie = numeroSerie;
    }

    public String getFirmware() {
        return firmware;
    }

    public void setFirmware(String firmware) {
        this.firmware = firmware;
    }

    public Integer getRssi() {
        return rssi;
    }

    public void setRssi(Integer rssi) {
        this.rssi = rssi;
    }
}
