package com.rehabit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<Integer, Set<SseEmitter>> assinantes = new ConcurrentHashMap<>();

    public SseEmitter inscrever(Integer idClinica) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        assinantes.computeIfAbsent(idClinica, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> remover(idClinica, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remover(idClinica, emitter);
        });
        emitter.onError(erro -> remover(idClinica, emitter));

        return emitter;
    }

    public void publicar(Integer idClinica, String evento, Object dados) {
        Set<SseEmitter> destinos = assinantes.get(idClinica);
        if (destinos == null || destinos.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : destinos) {
            try {
                emitter.send(SseEmitter.event().name(evento).data(dados));
            } catch (IOException | IllegalStateException erro) {
                // Aba fechada ou conexão derrubada no meio do envio: some com o
                // emitter em silêncio, o navegador reabre se ainda estiver vivo.
                remover(idClinica, emitter);
            }
        }
    }

    /** Há alguém com a tela aberta olhando esta clínica agora? */
    public boolean temOuvintes(Integer idClinica) {
        Set<SseEmitter> destinos = assinantes.get(idClinica);
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
            for (SseEmitter emitter : destinos) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException erro) {
                    remover(idClinica, emitter);
                }
            }
        });
    }

    private void remover(Integer idClinica, SseEmitter emitter) {
        Set<SseEmitter> destinos = assinantes.get(idClinica);
        if (destinos == null) {
            return;
        }
        destinos.remove(emitter);
        if (destinos.isEmpty()) {
            assinantes.remove(idClinica, destinos);
        }
        log.debug("SSE do goniometro encerrado para a clinica {}", idClinica);
    }
}
