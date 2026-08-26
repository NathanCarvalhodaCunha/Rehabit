package com.rehabit.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Pacote enviado pelo ESP32 a cada amostra. Só a clínica e o ângulo são
 * obrigatórios — o resto é telemetria de apoio e pode faltar em firmwares
 * antigos ou quando o hardware não tem o sensor correspondente (por exemplo,
 * uma placa alimentada só por USB não tem divisor de bateria).
 */
public class GoniometroTelemetriaDTO {

    @NotNull(message = "A clínica é obrigatória.")
    private Integer idClinica;

    @NotNull(message = "O ângulo é obrigatório.")
    private BigDecimal angulo;

    /** Ângulo antes do offset de tara, útil para diagnosticar montagem invertida. */
    private BigDecimal anguloBruto;

    private Integer bateria;
    private Integer rssi;
    private String numeroSerie;
    private String firmware;
    private String ip;

    /** true depois que o giroscópio terminou a calibração inicial do boot. */
    private Boolean calibrado;

    public GoniometroTelemetriaDTO() {
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }

    public BigDecimal getAngulo() {
        return angulo;
    }

    public void setAngulo(BigDecimal angulo) {
        this.angulo = angulo;
    }

    public BigDecimal getAnguloBruto() {
        return anguloBruto;
    }

    public void setAnguloBruto(BigDecimal anguloBruto) {
        this.anguloBruto = anguloBruto;
    }

    public Integer getBateria() {
        return bateria;
    }

    public void setBateria(Integer bateria) {
        this.bateria = bateria;
    }

    public Integer getRssi() {
        return rssi;
    }

    public void setRssi(Integer rssi) {
        this.rssi = rssi;
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

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Boolean getCalibrado() {
        return calibrado;
    }

    public void setCalibrado(Boolean calibrado) {
        this.calibrado = calibrado;
    }
}
