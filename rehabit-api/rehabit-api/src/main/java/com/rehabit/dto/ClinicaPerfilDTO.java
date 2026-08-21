package com.rehabit.dto;

public class ClinicaPerfilDTO {

    private Integer id;
    private String nome;
    private String cnpj;
    private String email;
    private String telefone;
    private String endereco;
    private String subtitulo;
    private String descricao;
    private String foto;
    private long profissionaisAtivos;
    private long pacientesTotais;
    private long sessoesEsteMes;
    private Double amplitudeMediaGeral;

    public ClinicaPerfilDTO() {
    }

    public ClinicaPerfilDTO(Integer id, String nome, String cnpj, String email, String telefone,
                             String endereco, String subtitulo, String descricao, String foto,
                             long profissionaisAtivos, long pacientesTotais, long sessoesEsteMes,
                             Double amplitudeMediaGeral) {
        this.id = id;
        this.nome = nome;
        this.cnpj = cnpj;
        this.email = email;
        this.telefone = telefone;
        this.endereco = endereco;
        this.subtitulo = subtitulo;
        this.descricao = descricao;
        this.foto = foto;
        this.profissionaisAtivos = profissionaisAtivos;
        this.pacientesTotais = pacientesTotais;
        this.sessoesEsteMes = sessoesEsteMes;
        this.amplitudeMediaGeral = amplitudeMediaGeral;
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

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getSubtitulo() {
        return subtitulo;
    }

    public void setSubtitulo(String subtitulo) {
        this.subtitulo = subtitulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public long getProfissionaisAtivos() {
        return profissionaisAtivos;
    }

    public void setProfissionaisAtivos(long profissionaisAtivos) {
        this.profissionaisAtivos = profissionaisAtivos;
    }

    public long getPacientesTotais() {
        return pacientesTotais;
    }

    public void setPacientesTotais(long pacientesTotais) {
        this.pacientesTotais = pacientesTotais;
    }

    public long getSessoesEsteMes() {
        return sessoesEsteMes;
    }

    public void setSessoesEsteMes(long sessoesEsteMes) {
        this.sessoesEsteMes = sessoesEsteMes;
    }

    public Double getAmplitudeMediaGeral() {
        return amplitudeMediaGeral;
    }

    public void setAmplitudeMediaGeral(Double amplitudeMediaGeral) {
        this.amplitudeMediaGeral = amplitudeMediaGeral;
    }
}
