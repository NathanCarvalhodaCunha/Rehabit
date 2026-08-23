package com.rehabit.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tb02_fisioterapeuta")
public class Fisioterapeuta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb02_id_fisioterapeuta")
    private Integer id;

    @Column(name = "tb02_nome_fisioterapeuta", nullable = false, length = 150)
    private String nome;

    @Column(name = "tb02_COFFITO", length = 20, unique = true)
    private String coffito;

    @Column(name = "tb02_telefone_fisioterapeuta", length = 20)
    private String telefone;

    @Column(name = "tb02_email_fisioterapeuta", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "tb02_senha_fisioterapeuta", nullable = false, length = 255)
    private String senha;

    @Column(name = "tb02_descricao_fisioterapeuta", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "tb02_especialidade", length = 100)
    private String especialidade;

    @Column(name = "tb02_foto_fisioterapeuta", length = 255)
    private String foto;

    @Column(name = "tb02_tutorial_visto", columnDefinition = "boolean not null default false")
    private boolean tutorialVisto;

    @Column(name = "tb02_id_clinica", nullable = false)
    private Integer idClinica;

    @Column(name = "tb02_localidade", length = 100)
    private String localidade;

    public Fisioterapeuta() {
    }

    // Getters e Setters

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

    public String getCoffito() {
        return coffito;
    }

    public void setCoffito(String coffito) {
        this.coffito = coffito;
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

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }

    public String getLocalidade() {
        return localidade;
    }

    public void setLocalidade(String localidade) {
        this.localidade = localidade;
    }

    public boolean isTutorialVisto() {
        return tutorialVisto;
    }

    public void setTutorialVisto(boolean tutorialVisto) {
        this.tutorialVisto = tutorialVisto;
    }
}
