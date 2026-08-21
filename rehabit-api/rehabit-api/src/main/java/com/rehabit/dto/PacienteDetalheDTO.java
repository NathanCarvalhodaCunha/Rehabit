package com.rehabit.dto;

import java.time.LocalDate;

public class PacienteDetalheDTO {

    private Integer id;
    private String nome;
    private String cpf;
    private String telefone;
    private String email;
    private LocalDate dataNascimento;
    private Integer idade;
    private String sexo;
    private LocalDate dataInicioTratamento;
    private String situacao;
    private String status;
    private Integer idFisioterapeuta;
    private String nomeFisioterapeuta;

    public PacienteDetalheDTO() {
    }

    public PacienteDetalheDTO(Integer id, String nome, String cpf, String telefone, String email,
                               LocalDate dataNascimento, Integer idade, String sexo,
                               LocalDate dataInicioTratamento, String situacao, String status,
                               Integer idFisioterapeuta, String nomeFisioterapeuta) {
        this.id = id;
        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
        this.email = email;
        this.dataNascimento = dataNascimento;
        this.idade = idade;
        this.sexo = sexo;
        this.dataInicioTratamento = dataInicioTratamento;
        this.situacao = situacao;
        this.status = status;
        this.idFisioterapeuta = idFisioterapeuta;
        this.nomeFisioterapeuta = nomeFisioterapeuta;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public Integer getIdade() {
        return idade;
    }

    public void setIdade(Integer idade) {
        this.idade = idade;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }

    public String getNomeFisioterapeuta() {
        return nomeFisioterapeuta;
    }

    public void setNomeFisioterapeuta(String nomeFisioterapeuta) {
        this.nomeFisioterapeuta = nomeFisioterapeuta;
    }
}
