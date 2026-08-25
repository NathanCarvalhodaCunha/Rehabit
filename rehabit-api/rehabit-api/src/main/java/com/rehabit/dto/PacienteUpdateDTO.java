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
