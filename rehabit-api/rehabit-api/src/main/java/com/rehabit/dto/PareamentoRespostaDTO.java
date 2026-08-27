package com.rehabit.dto;

/** O que o goniômetro grava na memória depois de parear. */
public class PareamentoRespostaDTO {

    private Integer idDispositivo;
    private Integer idClinica;
    private String nomeClinica;
    private String token;

    public PareamentoRespostaDTO() {
    }

    public PareamentoRespostaDTO(Integer idDispositivo, Integer idClinica, String nomeClinica, String token) {
        this.idDispositivo = idDispositivo;
        this.idClinica = idClinica;
        this.nomeClinica = nomeClinica;
        this.token = token;
    }

    public Integer getIdDispositivo() { return idDispositivo; }
    public void setIdDispositivo(Integer idDispositivo) { this.idDispositivo = idDispositivo; }
    public Integer getIdClinica() { return idClinica; }
    public void setIdClinica(Integer idClinica) { this.idClinica = idClinica; }
    public String getNomeClinica() { return nomeClinica; }
    public void setNomeClinica(String nomeClinica) { this.nomeClinica = nomeClinica; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
