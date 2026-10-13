package com.rehabit.dto;

import com.rehabit.security.SenhaForte;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class FisioterapeutaCreateDTO {

    @NotNull(message = "A instituição responsável é obrigatória.")
    private Integer idClinica;

    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 150)
    private String nome;

    @NotBlank(message = "O COFFITO é obrigatório.")
    @Size(max = 20)
    private String coffito;

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Informe um e-mail válido.")
    private String email;

    @NotBlank(message = "A senha é obrigatória.")
    @SenhaForte
    private String senha;

    @Size(max = 20, message = "O telefone pode ter no máximo 20 caracteres.")
    private String telefone;
    @Size(max = 100, message = "A especialidade pode ter no máximo 100 caracteres.")
    private String especialidade;
    @Size(max = 5000, message = "A descrição pode ter no máximo 5000 caracteres.")
    private String descricao;
    @Size(max = 100, message = "A localidade pode ter no máximo 100 caracteres.")
    private String localidade;
    @Size(max = 255, message = "O endereço da foto pode ter no máximo 255 caracteres.")
    private String foto;

    public FisioterapeutaCreateDTO() {
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
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

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
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

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getLocalidade() {
        return localidade;
    }

    public void setLocalidade(String localidade) {
        this.localidade = localidade;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }
}
