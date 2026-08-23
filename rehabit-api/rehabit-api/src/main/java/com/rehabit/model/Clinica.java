package com.rehabit.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tb01_clinica")
public class Clinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb01_id_clinica")
    private Integer id;

    @Column(name = "tb01_nome_clinica", nullable = false, length = 150)
    private String nome;

    @Column(name = "tb01_CNPJ", length = 18, unique = true)
    private String cnpj;

    @Column(name = "tb01_endereco_clinica", length = 200)
    private String endereco;

    @Column(name = "tb01_telefone_clinica", length = 20)
    private String telefone;

    @Column(name = "tb01_email_clinica", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "tb01_senha_clinica", nullable = false, length = 255)
    private String senha;

    @Column(name = "tb01_descricao_clinica", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "tb01_subtitulo", length = 150)
    private String subtitulo;

    @Column(name = "tb01_foto_clinica", length = 255)
    private String foto;

    @Column(name = "tb01_tutorial_visto", columnDefinition = "boolean not null default false")
    private boolean tutorialVisto;

    public Clinica() {
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

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
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

    public String getSubtitulo() {
        return subtitulo;
    }

    public void setSubtitulo(String subtitulo) {
        this.subtitulo = subtitulo;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public boolean isTutorialVisto() {
        return tutorialVisto;
    }

    public void setTutorialVisto(boolean tutorialVisto) {
        this.tutorialVisto = tutorialVisto;
    }
}
