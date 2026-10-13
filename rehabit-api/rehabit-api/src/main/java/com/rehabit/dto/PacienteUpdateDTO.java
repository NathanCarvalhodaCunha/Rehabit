package com.rehabit.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class PacienteUpdateDTO {

    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 150, message = "O nome pode ter no máximo 150 caracteres.")
    private String nome;

    @Size(max = 20, message = "O telefone pode ter no máximo 20 caracteres.")
    private String telefone;
    @Size(max = 150, message = "O e-mail pode ter no máximo 150 caracteres.")
    private String email;
    private LocalDate dataNascimento;
    @Size(max = 20, message = "O sexo pode ter no máximo 20 caracteres.")
    private String sexo;
    @Size(max = 50, message = "A situação pode ter no máximo 50 caracteres.")
    private String situacao;
    @Size(max = 255, message = "O endereço da foto pode ter no máximo 255 caracteres.")
    private String foto;
    /** Em tratamento (Ativo), Alta ou Inativo. */
    @Size(max = 50, message = "O status pode ter no máximo 50 caracteres.")
    private String status;

    // Anamnese e meta de tratamento
    @Size(max = 10000, message = "A queixa principal pode ter no máximo 10000 caracteres.")
    private String queixaPrincipal;
    @Size(max = 10000, message = "O histórico clínico pode ter no máximo 10000 caracteres.")
    private String historicoClinico;
    @Size(max = 10000, message = "O campo de medicamentos pode ter no máximo 10000 caracteres.")
    private String medicamentos;
    @Size(max = 10000, message = "O campo de contraindicações pode ter no máximo 10000 caracteres.")
    private String contraindicacoes;
    @Digits(integer = 3, fraction = 2, message = "A meta de amplitude aceita até duas casas decimais.")
    @DecimalMin(value = "0.0", message = "A meta de amplitude não pode ser negativa.")
    @DecimalMax(value = "360.0", message = "A meta de amplitude não pode passar de 360°.")
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
