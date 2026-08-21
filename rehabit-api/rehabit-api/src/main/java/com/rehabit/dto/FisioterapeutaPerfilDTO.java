package com.rehabit.dto;

public class FisioterapeutaPerfilDTO {

    private Integer id;
    private String nome;
    private String coffito;
    private String email;
    private String telefone;
    private String especialidade;
    private String localidade;
    private String descricao;
    private String foto;
    private Integer idClinica;
    private long pacientesAtivos;
    private long sessoesEsteMes;
    private Double amplitudeMediaGeral;

    public FisioterapeutaPerfilDTO() {
    }

    public FisioterapeutaPerfilDTO(Integer id, String nome, String coffito, String email, String telefone,
                                    String especialidade, String localidade, String descricao, String foto,
                                    Integer idClinica, long pacientesAtivos, long sessoesEsteMes,
                                    Double amplitudeMediaGeral) {
        this.id = id;
        this.nome = nome;
        this.coffito = coffito;
        this.email = email;
        this.telefone = telefone;
        this.especialidade = especialidade;
        this.localidade = localidade;
        this.descricao = descricao;
        this.foto = foto;
        this.idClinica = idClinica;
        this.pacientesAtivos = pacientesAtivos;
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

    public String getCoffito() {
        return coffito;
    }

    public void setCoffito(String coffito) {
        this.coffito = coffito;
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

    public String getEspecialidade() {
        return especialidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
    }

    public String getLocalidade() {
        return localidade;
    }

    public void setLocalidade(String localidade) {
        this.localidade = localidade;
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

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }

    public long getPacientesAtivos() {
        return pacientesAtivos;
    }

    public void setPacientesAtivos(long pacientesAtivos) {
        this.pacientesAtivos = pacientesAtivos;
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
