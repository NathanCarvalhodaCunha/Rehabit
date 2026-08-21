package com.rehabit.service;

import com.rehabit.dto.GoniometroDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Goniometro;
import com.rehabit.repository.GoniometroRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GoniometroService {

    private final GoniometroRepository goniometroRepository;

    public GoniometroService(GoniometroRepository goniometroRepository) {
        this.goniometroRepository = goniometroRepository;
    }

    public GoniometroDTO buscarUltimo(Integer idClinica) {
        Goniometro goniometro = goniometroRepository.findFirstByIdClinicaOrderByIdDesc(idClinica)
                .orElseThrow(() -> new AuthException("Nenhum dispositivo sincronizado ainda.", HttpStatus.NOT_FOUND));
        return paraDTO(goniometro);
    }

    public GoniometroDTO sincronizar(Integer idClinica) {
        Goniometro goniometro = new Goniometro();
        goniometro.setIdClinica(idClinica);
        goniometro.setBateria(ThreadLocalRandom.current().nextInt(60, 101));
        goniometro.setDataSincronizacao(LocalDate.now());
        goniometro.setHoraSincronizacao(LocalTime.now().withNano(0));
        return paraDTO(goniometroRepository.save(goniometro));
    }

    private GoniometroDTO paraDTO(Goniometro g) {
        return new GoniometroDTO(g.getId(), g.getBateria(), g.getDataSincronizacao(), g.getHoraSincronizacao(),
                g.getIdClinica());
    }
}
