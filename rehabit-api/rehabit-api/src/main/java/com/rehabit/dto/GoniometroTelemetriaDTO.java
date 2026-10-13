package com.rehabit.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Pacote enviado pelo ESP32 a cada amostra. Só a clínica e o ângulo são
 * obrigatórios — o resto é telemetria de apoio e pode faltar em firmwares
 * antigos ou quando o hardware não tem o sensor correspondente (por exemplo,
 * uma placa alimentada só por USB não tem divisor de bateria).
 */
public class GoniometroTelemetriaDTO {

    /**
     * Obrigatório só quando quem envia é a clínica logada. Um goniômetro
     * pareado NÃO manda este campo de propósito: a clínica sai do token dele,
     * então um aparelho não consegue mandar telemetria para a clínica de
     * outro. A exigência para o caminho da clínica está no GoniometroController.
     */
    private Integer idClinica;

    @NotNull(message = "O ângulo é obrigatório.")
    @Digits(integer = 3, fraction = 6, message = "Ângulo em formato inválido.")
    @DecimalMin(value = "-5", message = "Ângulo fora da faixa do goniômetro (0° a 180°).")
    @DecimalMax(value = "185", message = "Ângulo fora da faixa do goniômetro (0° a 180°).")
    private BigDecimal angulo;

    /** Ângulo antes do offset de tara, útil para diagnosticar montagem invertida. */
    @Digits(integer = 3, fraction = 6, message = "Ângulo bruto em formato inválido.")
    @DecimalMin(value = "-360", message = "Ângulo bruto fora da faixa.")
    @DecimalMax(value = "360", message = "Ângulo bruto fora da faixa.")
    private BigDecimal anguloBruto;

    private Integer bateria;
    private Integer rssi;
    @Size(max = 32, message = "O número de série pode ter no máximo 32 caracteres.")
    private String numeroSerie;
    @Size(max = 32, message = "A versão do firmware pode ter no máximo 32 caracteres.")
    private String firmware;
    @Size(max = 45, message = "O IP pode ter no máximo 45 caracteres.")
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
