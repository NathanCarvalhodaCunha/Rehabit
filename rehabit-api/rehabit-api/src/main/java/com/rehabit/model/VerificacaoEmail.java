package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Código enviado ao e-mail informado no cadastro. É o que garante que o
 * endereço existe de verdade: sem abrir aquela caixa de entrada não há
 * como saber o código, e sem o código a conta não é criada.
 *
 * Um registro por e-mail (o pedido novo sobrescreve o anterior), e o
 * registro é apagado assim que a conta é criada.
 */
@Entity
@Table(name = "tb12_verificacao_email")
public class VerificacaoEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb12_id_verificacao")
    private Integer id;

    @Column(name = "tb12_email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "tb12_codigo_hash", nullable = false, length = 64)
    private String codigoHash;

    @Column(name = "tb12_tentativas", nullable = false)
    private int tentativas;

    @Column(name = "tb12_criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "tb12_expira_em", nullable = false)
    private LocalDateTime expiraEm;

    /** Preenchido quando a pessoa acerta o código; o cadastro só passa depois disso. */
    @Column(name = "tb12_verificado_em")
    private LocalDateTime verificadoEm;

    public VerificacaoEmail() {
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

    public LocalDateTime getVerificadoEm() {
        return verificadoEm;
    }

    public void setVerificadoEm(LocalDateTime verificadoEm) {
        this.verificadoEm = verificadoEm;
    }
}
