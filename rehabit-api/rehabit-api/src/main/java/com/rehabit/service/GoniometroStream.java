package com.rehabit.service;

import com.rehabit.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Canal de push do servidor para as telas (Server-Sent Events).
 *
 * Uma conexão SSE é aberta por aba que estiver olhando o goniômetro, e cada
 * pacote que chega do ESP32 é reemitido na hora para todas as abas daquela
 * clínica — é isso que dá o "tempo real" sem a tela ficar perguntando de
 * dois em dois segundos. O polling continua existindo no cliente, mas só
 * como plano B para quando o SSE não sobe (proxy que corta streaming, por
 * exemplo).
 */
@Component
public class GoniometroStream {

    private static final Logger log = LoggerFactory.getLogger(GoniometroStream.class);

    /**
     * 30 min. O navegador reconecta sozinho quando o emitter expira, então um
     * teto aqui só serve para não deixar conexão órfã pendurada no servidor
     * quando a aba morre sem avisar (celular que dorme, rede que cai).
     */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    /**
     * Tetos de conexões abertas. Cada uma segura memória e entra no ping de
     * 20 s; sem teto, uma conta logada abria milhares e o servidor gastava
     * tudo nelas. Três por pessoa cobrem a tela aberta no computador, no
     * celular e uma aba esquecida — a quarta derruba a mais antiga dela, que
     * quase sempre é uma aba que já fechou sem avisar. O teto da clínica (e o
     * do servidor) recusa: aí não há o que derrubar sem tirar a tela de outra
     * pessoa.
     */
    static final int MAXIMO_POR_PESSOA = 3;
    static final int MAXIMO_POR_CLINICA = 20;
    static final int MAXIMO_NO_SERVIDOR = 500;

    /** Uma tela aberta: de quem é e em que ordem chegou. */
    private record Assinante(SseEmitter emitter, String pessoa, long ordem) {
    }

    private final Map<Integer, Set<Assinante>> assinantes = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicLong sequencia = new AtomicLong();

    /** @param pessoa quem está abrindo (tipo e id da conta), para o teto por pessoa. */
    public SseEmitter inscrever(Integer idClinica, String pessoa) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Assinante novo = new Assinante(emitter, pessoa, sequencia.incrementAndGet());
        List<Assinante> derrubados = new ArrayList<>();

        // compute() trava só a entrada desta clínica: conferir os tetos, tirar
        // as mais antigas e entrar acontecem juntos, sem outra tela no meio.
        // A exceção do teto sai daqui sem mexer no mapa.
        assinantes.compute(idClinica, (chave, atuais) -> {
            Set<Assinante> daClinica = atuais != null ? atuais : ConcurrentHashMap.newKeySet();
            List<Assinante> dela = daClinica.stream()
                    .filter(a -> a.pessoa().equals(pessoa))
                    .sorted(Comparator.comparingLong(Assinante::ordem))
                    .collect(Collectors.toList());
            List<Assinante> sobrando = dela.subList(0, Math.max(0, dela.size() - (MAXIMO_POR_PESSOA - 1)));
            if (daClinica.size() - sobrando.size() + 1 > MAXIMO_POR_CLINICA
                    || total.get() - sobrando.size() + 1 > MAXIMO_NO_SERVIDOR) {
                throw new AuthException("Muitas telas abertas acompanhando o goniômetro. Feche alguma e tente de novo.",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
            derrubados.addAll(sobrando);
            derrubados.forEach(daClinica::remove);
            total.addAndGet(1 - derrubados.size());
            daClinica.add(novo);
            return daClinica;
        });
        derrubados.forEach(a -> a.emitter().complete());

        emitter.onCompletion(() -> tirar(idClinica, novo));
        emitter.onTimeout(() -> {
            emitter.complete();
            tirar(idClinica, novo);
        });
        emitter.onError(erro -> tirar(idClinica, novo));

        return emitter;
    }

    /** Quantas telas estão abertas agora (para os testes e para diagnóstico). */
    public int abertas(Integer idClinica) {
        Set<Assinante> daClinica = assinantes.get(idClinica);
        return daClinica == null ? 0 : daClinica.size();
    }

    public void publicar(Integer idClinica, String evento, Object dados) {
        Set<Assinante> destinos = assinantes.get(idClinica);
        if (destinos == null || destinos.isEmpty()) {
            return;
        }
        for (Assinante destino : destinos) {
            try {
                destino.emitter().send(SseEmitter.event().name(evento).data(dados));
            } catch (IOException | IllegalStateException erro) {
                // Aba fechada ou conexão derrubada no meio do envio: some com o
                // emitter em silêncio, o navegador reabre se ainda estiver vivo.
                tirar(idClinica, destino);
            }
        }
    }

    /** Há alguém com a tela aberta olhando esta clínica agora? */
    public boolean temOuvintes(Integer idClinica) {
        Set<Assinante> destinos = assinantes.get(idClinica);
        return destinos != null && !destinos.isEmpty();
    }

    /**
     * Comentário SSE periódico. Proxies e balanceadores (o Render inclusive)
     * fecham conexões ociosas; um byte a cada 20s mantém o cano aberto sem
     * virar um evento que o cliente precise tratar.
     */
    @Scheduled(fixedRate = 20000)
    public void manterVivo() {
        assinantes.forEach((idClinica, destinos) -> {
            for (Assinante destino : destinos) {
                try {
                    destino.emitter().send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException erro) {
                    tirar(idClinica, destino);
                }
            }
        });
    }

    private void tirar(Integer idClinica, Assinante assinante) {
        assinantes.computeIfPresent(idClinica, (chave, destinos) -> {
            if (destinos.remove(assinante)) {
                total.decrementAndGet();
            }
            return destinos.isEmpty() ? null : destinos;
        });
        log.debug("SSE do goniometro encerrado para a clinica {}", idClinica);
    }
}
