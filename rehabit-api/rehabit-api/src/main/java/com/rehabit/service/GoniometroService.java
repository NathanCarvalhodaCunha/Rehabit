package com.rehabit.service;

import com.rehabit.dto.GoniometroDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Goniometro;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.GoniometroRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GoniometroService {

    private record Leitura(BigDecimal angulo, java.time.Instant recebidaEm) {}

    private static final long VALIDADE_LEITURA_SEGUNDOS = 30;

    private final GoniometroRepository goniometroRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final Map<Integer, Leitura> ultimoAnguloPorClinica = new ConcurrentHashMap<>();

    public GoniometroService(GoniometroRepository goniometroRepository,
                              FisioterapeutaRepository fisioterapeutaRepository) {
        this.goniometroRepository = goniometroRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
    }

    public GoniometroDTO buscarUltimo(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        Goniometro goniometro = goniometroRepository.findFirstByIdClinicaOrderByIdDesc(idClinica)
                .orElseThrow(() -> new AuthException("Nenhum dispositivo sincronizado ainda.", HttpStatus.NOT_FOUND));
        return paraDTO(goniometro);
    }

    public GoniometroDTO sincronizar(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        Goniometro goniometro = new Goniometro();
        goniometro.setIdClinica(idClinica);
        goniometro.setBateria(ThreadLocalRandom.current().nextInt(60, 101));
        goniometro.setDataSincronizacao(LocalDate.now());
        goniometro.setHoraSincronizacao(LocalTime.now().withNano(0));
        return paraDTO(goniometroRepository.save(goniometro));
    }

    public void registrarLeitura(Integer idClinica, Integer usuarioId, String usuarioTipo, BigDecimal angulo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        ultimoAnguloPorClinica.put(idClinica, new Leitura(angulo, java.time.Instant.now()));
    }

    /**
     * Leitura vinda de um goniômetro pareado: a clínica veio do token do
     * próprio aparelho e a posse já foi conferida ao validar o dispositivo,
     * então não há o que checar de novo aqui.
     */
    public void registrarLeituraDeDispositivo(Integer idClinica, BigDecimal angulo) {
        ultimoAnguloPorClinica.put(idClinica, new Leitura(angulo, java.time.Instant.now()));
    }

    public BigDecimal buscarLeituraAtual(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        Leitura leitura = ultimoAnguloPorClinica.get(idClinica);
        if (leitura == null) {
            return null;
        }
        boolean expirada = leitura.recebidaEm().isBefore(java.time.Instant.now().minusSeconds(VALIDADE_LEITURA_SEGUNDOS));
        return expirada ? null : leitura.angulo();
    }

    /** A própria clínica, ou um fisioterapeuta que pertence a ela (dispositivo é compartilhado pela clínica toda). */
    private void verificarPosseClinica(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        if ("CLINICA".equals(usuarioTipo) && idClinica.equals(usuarioId)) {
            return;
        }
        if ("FISIOTERAPEUTA".equals(usuarioTipo)) {
            Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(usuarioId).orElse(null);
            if (fisioterapeuta != null && idClinica.equals(fisioterapeuta.getIdClinica())) {
                return;
            }
        }
        throw new AuthException("Você não tem acesso a este recurso.", HttpStatus.FORBIDDEN);
    }

    private GoniometroDTO paraDTO(Goniometro g) {
        return new GoniometroDTO(g.getId(), g.getBateria(), g.getDataSincronizacao(), g.getHoraSincronizacao(),
                g.getIdClinica());
    }
}
