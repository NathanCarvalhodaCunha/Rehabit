package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "tb03_paciente")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb03_id_paciente")
    private Integer id;

    @Column(name = "tb03_nome_paciente", nullable = false, length = 150)
    private String nome;

    @Column(name = "tb03_CPF", nullable = false, unique = true, length = 14)
    private String cpf;

    @Column(name = "tb03_telefone_paciente", length = 20)
    private String telefone;

    @Column(name = "tb03_email_paciente", length = 150)
    private String email;

    @Column(name = "tb03_data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "tb03_sexo", length = 20)
    private String sexo;

    @Column(name = "tb03_data_inicio_tratamento")
    private LocalDate dataInicioTratamento;

    @Column(name = "tb03_situacao", length = 50)
    private String situacao;

    @Column(name = "tb03_status", length = 50)
    private String status;

    @Column(name = "tb03_id_clinica", nullable = false)
    private Integer idClinica;

    @Column(name = "tb03_id_fisioterapeuta", nullable = false)
    private Integer idFisioterapeuta;

    public Paciente() {
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

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }
}
