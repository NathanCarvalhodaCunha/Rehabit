package com.rehabit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Conclusão da recuperação de senha. Aceita as duas formas de provar que a
 * pessoa recebeu o e-mail: o token do link ou o e-mail mais o código de 6
 * dígitos digitado na tela. Quais campos são obrigatórios depende de qual
 * das duas veio, então a checagem fica no service, não em anotação.
 */
public class RedefinirSenhaRequestDTO {

    private String token;

    @Email(message = "Informe um e-mail válido.")
    private String email;

    private String codigo;

    @NotBlank(message = "A nova senha é obrigatória.")
    @Size(min = 6, message = "A senha deve ter ao menos 6 caracteres.")
    private String novaSenha;

    public RedefinirSenhaRequestDTO() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNovaSenha() {
        return novaSenha;
    }

    public void setNovaSenha(String novaSenha) {
        this.novaSenha = novaSenha;
    }
}
