package com.rehabit.security;

import java.time.Duration;

/**
 * Quantas tentativas cabem em cada janela, por rota e por chave (IP, conta ou
 * o sistema todo). Os números são folgados para o uso real de uma clínica,
 * que costuma ter a equipe inteira atrás do mesmo IP, e apertados para quem
 * tenta adivinhar senha, código ou esgotar a cota de e-mails.
 *
 * A mensagem leva um %s, trocado por quanto falta para a janela acabar.
 */
public enum Limite {

    LOGIN_POR_IP(20, Duration.ofMinutes(1),
            "Muitas tentativas de login a partir desta rede. Tente de novo em %s."),
    /**
     * Só conta senha errada, e vale com ou sem conta para aquele e-mail: a
     * resposta igual nos dois casos é o que impede de usar o limite para
     * descobrir quem tem conta. Por isso também barra a senha certa até a
     * janela acabar — senão o 429 de um palpite e o 200 do seguinte
     * entregariam a senha.
     */
    LOGIN_POR_CONTA(10, Duration.ofMinutes(15),
            "Muitas tentativas de login nesta conta. Tente de novo em %s ou use \"Esqueci minha senha\"."),
    CADASTRO_POR_IP(10, Duration.ofHours(1),
            "Muitos cadastros a partir desta rede. Tente de novo em %s."),
    /** Os dois envios (código de cadastro e recuperação) gastam a mesma cota diária do Brevo. */
    EMAIL_POR_IP(20, Duration.ofHours(1),
            "Muitos pedidos de e-mail a partir desta rede. Tente de novo em %s."),
    /** Conta todo pedido, exista ou não a conta, pela mesma razão do LOGIN_POR_CONTA. */
    EMAIL_POR_DESTINATARIO(5, Duration.ofHours(1),
            "Muitos e-mails pedidos para este endereço. Tente de novo em %s."),
    CODIGO_POR_IP(30, Duration.ofMinutes(10),
            "Muitas tentativas de código a partir desta rede. Tente de novo em %s."),
    /** Só conta código errado: parear certo não gasta a cota de quem configura vários aparelhos. */
    PAREAMENTO_POR_IP(10, Duration.ofMinutes(10),
            "Muitos códigos de pareamento errados a partir desta rede. Tente de novo em %s."),
    /**
     * Teto de códigos errados no sistema todo, de qualquer IP. É o que segura
     * quem espalha os palpites por muitos endereços: com 30 por minuto, os 10
     * minutos de validade de um código rendem no máximo 300 dos 1.000.000
     * possíveis (0,03% de chance de acertar).
     */
    PAREAMENTO_GLOBAL(30, Duration.ofMinutes(1),
            "Muitas tentativas de pareamento no sistema agora. Tente de novo em %s."),
    UPLOAD_POR_IP(30, Duration.ofMinutes(10),
            "Muitos envios de arquivo a partir desta rede. Tente de novo em %s."),
    /** A foto do cadastro: quem escolhe uma, desiste e escolhe outra cabe com folga. */
    FOTO_DO_CADASTRO_POR_EMAIL(5, Duration.ofHours(1),
            "Muitas fotos enviadas para este cadastro. Tente de novo em %s."),
    /** Cada reconexão do SSE pede um ingresso; isto também limita a memória que eles ocupam. */
    INGRESSO_POR_USUARIO(30, Duration.ofMinutes(1),
            "Muitas reconexões ao goniômetro. Tente de novo em %s.");

    private final int maximo;
    private final Duration janela;
    private final String mensagem;

    Limite(int maximo, Duration janela, String mensagem) {
        this.maximo = maximo;
        this.janela = janela;
        this.mensagem = mensagem;
    }

    public int maximo() {
        return maximo;
    }

    public Duration janela() {
        return janela;
    }

    String mensagem(long segundosRestantes) {
        return String.format(mensagem, tempoPorExtenso(segundosRestantes));
    }

    private static String tempoPorExtenso(long segundos) {
        if (segundos < 60) {
            return segundos == 1 ? "1 segundo" : segundos + " segundos";
        }
        long minutos = (segundos + 59) / 60;
        return minutos == 1 ? "1 minuto" : minutos + " minutos";
    }
}
