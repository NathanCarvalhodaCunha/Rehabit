package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Uma foto que acabou de ser enviada e ainda pode nunca ser usada: a pessoa
 * escolhe a foto, ela sobe na hora, e o cadastro pode nem ser salvo. Sem este
 * registro, esses arquivos ficavam para sempre no armazenamento — e qualquer
 * conta logada podia enchê-lo de propósito. A LimpezaDeEnvios apaga, depois de
 * um dia, o que nenhum cadastro passou a usar.
 *
 * Não pertence a clínica nenhuma: é só a lista do que ainda está "em
 * trânsito". Quem passa a usar a foto é o cadastro, e a partir daí quem cuida
 * dela é o FotosEmUso.
 */
@Entity
@Table(name = "tb13_arquivo_enviado")
public class ArquivoEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb13_id_arquivo")
    private Integer id;

    @Column(name = "tb13_url", nullable = false, unique = true, length = 255)
    private String url;

    @Column(name = "tb13_enviado_em", nullable = false)
    private LocalDateTime enviadoEm;

    public ArquivoEnviado() {
    }

    public ArquivoEnviado(String url, LocalDateTime enviadoEm) {
        this.url = url;
        this.enviadoEm = enviadoEm;
    }

    public Integer getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public LocalDateTime getEnviadoEm() {
        return enviadoEm;
    }
}
