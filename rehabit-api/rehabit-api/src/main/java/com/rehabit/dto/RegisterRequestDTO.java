package com.rehabit.dto;

import com.rehabit.security.SenhaForte;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequestDTO {

    @NotBlank(message = "O tipo de conta é obrigatório.")
    @Pattern(regexp = "profissional|instituicao", message = "Tipo deve ser 'profissional' ou 'instituicao'.")
    private String tipo;

    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 150)
    private String nome;

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Informe um e-mail válido.")
    private String email;

    @NotBlank(message = "A senha é obrigatória.")
    @SenhaForte
    private String senha;

    // Campos do pop-up "Informações opcionais", usados apenas quando
    // tipo = "instituicao". CNPJ é obrigatório (tb01_CNPJ é NOT NULL).
    @NotBlank(message = "O CNPJ é obrigatório.")
    @Size(max = 18, message = "O CNPJ pode ter no máximo 18 caracteres.")
    private String cnpj;
    @Size(max = 20, message = "O telefone pode ter no máximo 20 caracteres.")
    private String telefone;
    @Size(max = 200, message = "O endereço pode ter no máximo 200 caracteres.")
    private String endereco;
    @Size(max = 150, message = "O subtítulo pode ter no máximo 150 caracteres.")
    private String subtitulo;
    @Size(max = 5000, message = "A descrição pode ter no máximo 5000 caracteres.")
    private String descricao;
    @Size(max = 255, message = "O endereço da foto pode ter no máximo 255 caracteres.")
    private String foto;

    public RegisterRequestDTO() {
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
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

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
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
}
