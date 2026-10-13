package com.rehabit.storage;

import com.rehabit.model.ArquivoEnviado;
import com.rehabit.repository.ArquivoEnviadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Apaga as fotos enviadas que nenhum cadastro passou a usar (veja
 * ArquivoEnviado). Um dia de folga cobre quem escolheu a foto e só salvou o
 * cadastro bem depois; passa disso, o arquivo é lixo — ou abuso.
 */
@Component
public class LimpezaDeEnvios {

    private static final Logger log = LoggerFactory.getLogger(LimpezaDeEnvios.class);
    static final Duration FOLGA = Duration.ofDays(1);

    private final ArquivoEnviadoRepository enviados;
    private final FotosEmUso fotosEmUso;
    private final FileStorageService armazenamento;
    private final Clock relogio;

    public LimpezaDeEnvios(ArquivoEnviadoRepository enviados, FotosEmUso fotosEmUso,
                           FileStorageService armazenamento, Clock relogio) {
        this.enviados = enviados;
        this.fotosEmUso = fotosEmUso;
        this.armazenamento = armazenamento;
        this.relogio = relogio;
    }

    @Scheduled(initialDelayString = "${rehabit.uploads.limpeza-atraso-ms:300000}",
            fixedDelayString = "${rehabit.uploads.limpeza-intervalo-ms:3600000}")
    public void limparAgendado() {
        int apagados = limparEnviadosAntesDe(LocalDateTime.now(relogio).minus(FOLGA));
        if (apagados > 0) {
            log.info("Limpeza de envios: {} foto(s) que ninguém usou apagada(s).", apagados);
        }
    }

    /** @return quantos arquivos saíram do armazenamento. */
    public int limparEnviadosAntesDe(LocalDateTime limite) {
        int apagados = 0;
        List<ArquivoEnviado> vencidos;
        do {
            vencidos = enviados.findTop500ByEnviadoEmBeforeOrderByEnviadoEmAsc(limite);
            for (ArquivoEnviado enviado : vencidos) {
                // Em uso: o cadastro já cuida dele (FotosEmUso); só sai da lista.
                if (!fotosEmUso.emUso(enviado.getUrl())) {
                    armazenamento.excluir(enviado.getUrl());
                    apagados++;
                }
                enviados.delete(enviado);
            }
        } while (vencidos.size() == 500);
        return apagados;
    }
}
