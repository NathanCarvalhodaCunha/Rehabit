package com.rehabit.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public class PacienteUpdateDTO {

    @NotBlank(message = "O nome é obrigatório.")
    private String nome;

    private String telefone;
    private String email;
    private LocalDate dataNascimento;
    private String sexo;
    private String situacao;
    private String foto;
    /** Em tratamento (Ativo), Alta ou Inativo. */
    private String status;

    // Anamnese e meta de tratamento
    private String queixaPrincipal;
    private String historicoClinico;
    private String medicamentos;
    private String contraindicacoes;
    private java.math.BigDecimal metaAmplitude;
    private LocalDate metaData;

    public String getQueixaPrincipal() { return queixaPrincipal; }
    public void setQueixaPrincipal(String queixaPrincipal) { this.queixaPrincipal = queixaPrincipal; }
    public String getHistoricoClinico() { return historicoClinico; }
    public void setHistoricoClinico(String historicoClinico) { this.historicoClinico = historicoClinico; }
    public String getMedicamentos() { return medicamentos; }
    public void setMedicamentos(String medicamentos) { this.medicamentos = medicamentos; }
    public String getContraindicacoes() { return contraindicacoes; }
    public void setContraindicacoes(String contraindicacoes) { this.contraindicacoes = contraindicacoes; }
    public java.math.BigDecimal getMetaAmplitude() { return metaAmplitude; }
    public void setMetaAmplitude(java.math.BigDecimal metaAmplitude) { this.metaAmplitude = metaAmplitude; }
    public LocalDate getMetaData() { return metaData; }
    public void setMetaData(LocalDate metaData) { this.metaData = metaData; }

    public PacienteUpdateDTO() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public String getSexo() {
        return sexo;
    }

    public void setSexo(String sexo) {
        this.sexo = sexo;
    }

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }
}
