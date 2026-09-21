package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Cadastro do goniômetro de uma clínica. Uma linha por aparelho físico
 * (identificado pelo número de série que o ESP32 deriva do MAC), atualizada
 * a cada pacote de telemetria — não é um histórico de sincronizações.
 *
 * As colunas além das quatro originais do schema (bateria, data/hora de
 * sincronização, clínica) são criadas pelo `ddl-auto=update` do Hibernate;
 * o Rehabit.sql traz as mesmas colunas para quem cria o banco na mão.
 */
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

    /** Derivado do MAC do ESP32 pelo firmware, no formato XXXX-XXXX. */
    @Column(name = "tb04_numero_serie", length = 32)
    private String numeroSerie;

    @Column(name = "tb04_firmware", length = 32)
    private String firmware;

    /** Força do sinal Wi-Fi em dBm (negativo; quanto mais perto de 0, melhor). */
    @Column(name = "tb04_rssi")
    private Integer rssi;

    @Column(name = "tb04_ip", length = 45)
    private String ip;

    /** Momento do último pacote recebido do aparelho — base do status online/offline. */
    @Column(name = "tb04_ultimo_contato")
    private LocalDateTime ultimoContato;

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

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public LocalDateTime getUltimoContato() {
        return ultimoContato;
    }

    public void setUltimoContato(LocalDateTime ultimoContato) {
        this.ultimoContato = ultimoContato;
    }
}
