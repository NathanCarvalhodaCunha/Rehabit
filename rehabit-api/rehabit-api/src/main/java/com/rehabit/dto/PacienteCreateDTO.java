package com.rehabit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class PacienteCreateDTO {

    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 150, message = "O nome pode ter no máximo 150 caracteres.")
    private String nome;

    @NotBlank(message = "O CPF é obrigatório.")
    @Size(max = 14, message = "O CPF pode ter no máximo 14 caracteres.")
    private String cpf;

    @Size(max = 20, message = "O telefone pode ter no máximo 20 caracteres.")
    private String telefone;
    @Size(max = 150, message = "O e-mail pode ter no máximo 150 caracteres.")
    private String email;
    private LocalDate dataNascimento;
    @Size(max = 20, message = "O sexo pode ter no máximo 20 caracteres.")
    private String sexo;
    private LocalDate dataInicioTratamento;
    @Size(max = 50, message = "A situação pode ter no máximo 50 caracteres.")
    private String situacao;
    @Size(max = 255, message = "O endereço da foto pode ter no máximo 255 caracteres.")
    private String foto;

    @NotNull(message = "O profissional responsável é obrigatório.")
    private Integer idFisioterapeuta;

    public PacienteCreateDTO() {
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
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

    public LocalDate getDataInicioTratamento() {
        return dataInicioTratamento;
    }

    public void setDataInicioTratamento(LocalDate dataInicioTratamento) {
        this.dataInicioTratamento = dataInicioTratamento;
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

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }
}
