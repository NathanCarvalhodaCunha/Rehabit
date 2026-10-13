package com.rehabit.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contador de tentativas por janela fixa, em memória.
 *
 * Em memória porque a API roda numa instância só e o que se quer é frear
 * robô, não guardar histórico: um reinício zera tudo, e tudo bem. Sem
 * biblioteca nova — o mapa concorrente do Java resolve, e o compute() de cada
 * chave é atômico, então duas requisições simultâneas nunca contam como uma.
 *
 * As chaves vencidas saem numa faxina a cada minuto. Como parte das chaves
 * vem de quem chama (o e-mail digitado no login, por exemplo), o mapa também
 * tem um teto: passou dele mesmo depois da faxina, recomeça do zero. Perder a
 * contagem por um instante é melhor que derrubar a API por falta de memória.
 */
@Component
public class LimiteDeTentativas {

    private static final Logger log = LoggerFactory.getLogger(LimiteDeTentativas.class);

    static final int MAXIMO_DE_CHAVES = 100_000;
    private static final Duration INTERVALO_DA_FAXINA = Duration.ofMinutes(1);

    /** Chave dos limites que valem para o sistema todo, e não por IP ou conta. */
    public static final String TODOS = "*";

    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();
    private final Clock relogio;
    private final boolean ativo;
    private volatile Instant proximaFaxina;

    public LimiteDeTentativas(Clock relogio, @Value("${rehabit.limites.ativo:true}") boolean ativo) {
        this.relogio = relogio;
        this.ativo = ativo;
        this.proximaFaxina = relogio.instant().plus(INTERVALO_DA_FAXINA);
        if (!ativo) {
            log.warn("Limites de tentativas desligados (rehabit.limites.ativo=false).");
        }
    }

    /**
     * Conta esta tentativa e recusa se ela passou do limite. Para as rotas em
     * que toda chamada gasta (um e-mail enviado, um login tentado).
     *
     * @throws LimiteExcedidoException 429, com quanto falta para liberar
     */
    public void consumir(Limite limite, String chave) {
        if (!ativo) {
            return;
        }
        Instant agora = relogio.instant();
        Janela janela = somar(limite, chave, agora);
        if (janela.tentativas() > limite.maximo()) {
            throw excedido(limite, janela, agora);
        }
    }

    /**
     * Recusa se o limite já foi atingido, sem contar esta tentativa. Junto
     * com {@link #contar}, serve aos limites que só contam o que deu errado.
     */
    public void exigirFolga(Limite limite, String chave) {
        if (!ativo) {
            return;
        }
        Instant agora = relogio.instant();
        Janela janela = janelas.get(chaveDe(limite, chave));
        if (janela != null && !janela.acabou(agora) && janela.tentativas() >= limite.maximo()) {
            throw excedido(limite, janela, agora);
        }
    }

    /** Conta uma tentativa que deu errado (senha ou código incorreto). */
    public void contar(Limite limite, String chave) {
        if (ativo) {
            somar(limite, chave, relogio.instant());
        }
    }

    /** Esquece a contagem — por exemplo, das senhas erradas de quem acabou de redefinir a senha. */
    public void zerar(Limite limite, String chave) {
        janelas.remove(chaveDe(limite, chave));
    }

    private Janela somar(Limite limite, String chave, Instant agora) {
        Janela janela = janelas.compute(chaveDe(limite, chave), (k, atual) ->
                atual == null || atual.acabou(agora)
                        ? new Janela(agora.plus(limite.janela()), 1)
                        : new Janela(atual.fim(), atual.tentativas() + 1));
        if (!agora.isBefore(proximaFaxina) || janelas.size() > MAXIMO_DE_CHAVES) {
            faxinar(agora);
        }
        return janela;
    }

    private void faxinar(Instant agora) {
        proximaFaxina = agora.plus(INTERVALO_DA_FAXINA);
        janelas.values().removeIf(janela -> janela.acabou(agora));
        if (janelas.size() > MAXIMO_DE_CHAVES) {
            log.error("Limites de tentativas com mais de {} chaves vivas: recomeçando a contagem do zero. "
                    + "Sinal de ataque espalhado por muitos IPs ou e-mails.", MAXIMO_DE_CHAVES);
            janelas.clear();
        }
    }

    private static LimiteExcedidoException excedido(Limite limite, Janela janela, Instant agora) {
        // Arredonda para cima: dizer "tente em 0 segundos" e recusar de novo seria pior.
        long segundos = Math.max(1, (Duration.between(agora, janela.fim()).toMillis() + 999) / 1000);
        return new LimiteExcedidoException(limite.mensagem(segundos), segundos);
    }

    private static String chaveDe(Limite limite, String chave) {
        return limite.name() + '|' + (chave == null ? "" : chave);
    }

    private record Janela(Instant fim, int tentativas) {
        boolean acabou(Instant agora) {
            return !agora.isBefore(fim);
        }
    }
}
