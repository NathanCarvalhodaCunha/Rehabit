package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Pedido de recuperação de senha. Guarda só o hash SHA-256 do token (do
 * link) e do código de 6 dígitos: quem lê o banco não consegue trocar a
 * senha de ninguém, do mesmo jeito que acontece com a senha em si.
 *
 * O código existe além do token porque o site do Rehabit também roda
 * aberto direto do arquivo (file://), onde um link de e-mail não teria
 * para onde apontar — aí a pessoa digita os 6 dígitos na tela.
 */
@Entity
@Table(name = "tb11_recuperacao_senha")
public class RecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb11_id_recuperacao")
    private Integer id;

    @Column(name = "tb11_email", nullable = false, length = 150)
    private String email;

    /** CLINICA ou FISIOTERAPEUTA — a mesma conta pode estar em qualquer uma das duas tabelas. */
    @Column(name = "tb11_tipo_conta", nullable = false, length = 20)
    private String tipoConta;

    @Column(name = "tb11_token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "tb11_codigo_hash", nullable = false, length = 64)
    private String codigoHash;

    @Column(name = "tb11_tentativas", nullable = false)
    private int tentativas;

    @Column(name = "tb11_criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "tb11_expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "tb11_usado_em")
    private LocalDateTime usadoEm;

    public RecuperacaoSenha() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTipoConta() {
        return tipoConta;
    }

    public void setTipoConta(String tipoConta) {
        this.tipoConta = tipoConta;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getCodigoHash() {
        return codigoHash;
    }

    public void setCodigoHash(String codigoHash) {
        this.codigoHash = codigoHash;
    }

    public int getTentativas() {
        return tentativas;
    }

    public void setTentativas(int tentativas) {
        this.tentativas = tentativas;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(LocalDateTime expiraEm) {
        this.expiraEm = expiraEm;
    }

    public LocalDateTime getUsadoEm() {
        return usadoEm;
    }

    public void setUsadoEm(LocalDateTime usadoEm) {
        this.usadoEm = usadoEm;
    }
}
