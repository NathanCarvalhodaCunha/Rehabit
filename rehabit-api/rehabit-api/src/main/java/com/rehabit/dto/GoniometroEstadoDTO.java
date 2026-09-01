package com.rehabit.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Retrato completo do goniômetro para a tela: é o que vai tanto na resposta
 * de GET /goniometro/estado quanto em cada evento do SSE, para que a tela
 * tenha uma única forma de renderizar (não importa se o dado chegou por
 * push ou por polling de emergência).
 */
public class GoniometroEstadoDTO {

    private boolean conectado;
    private BigDecimal angulo;
    private BigDecimal anguloBruto;
    private Integer bateria;
    private Integer rssi;
    private String numeroSerie;
    private String firmware;
    private String ip;
    private Boolean calibrado;

    /** Epoch ms do último pacote, ou null se o aparelho nunca falou com este servidor. */
    private Long ultimoContato;
    private Long segundosDesdeContato;

    /** Data/hora da última sincronização manual, no formato dd/MM/yyyy HH:mm:ss. */
    private String sincronizadoEm;

    private GoniometroCapturaDTO captura;

    /** Últimos segundos de leitura, do mais antigo para o mais novo (para o gráfico). */
    private List<GoniometroAmostraDTO> historico;

    public GoniometroEstadoDTO() {
    }

    public boolean isConectado() {
        return conectado;
    }

    public void setConectado(boolean conectado) {
        this.conectado = conectado;
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

    public Long getUltimoContato() {
        return ultimoContato;
    }

    public void setUltimoContato(Long ultimoContato) {
        this.ultimoContato = ultimoContato;
    }

    public Long getSegundosDesdeContato() {
        return segundosDesdeContato;
    }

    public void setSegundosDesdeContato(Long segundosDesdeContato) {
        this.segundosDesdeContato = segundosDesdeContato;
    }

    public String getSincronizadoEm() {
        return sincronizadoEm;
    }

    public void setSincronizadoEm(String sincronizadoEm) {
        this.sincronizadoEm = sincronizadoEm;
    }

    public GoniometroCapturaDTO getCaptura() {
        return captura;
    }

    public void setCaptura(GoniometroCapturaDTO captura) {
        this.captura = captura;
    }

    public List<GoniometroAmostraDTO> getHistorico() {
        return historico;
    }

    public void setHistorico(List<GoniometroAmostraDTO> historico) {
        this.historico = historico;
    }
}
