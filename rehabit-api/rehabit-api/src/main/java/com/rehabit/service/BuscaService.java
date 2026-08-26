package com.rehabit.service;

import com.rehabit.dto.BuscaResultadoDTO;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.PacienteRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Busca por nome, restrita ao que a conta logada pode ver. */
@Service
public class BuscaService {

    private static final int LIMITE = 12;

    private final PacienteRepository pacienteRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;

    public BuscaService(PacienteRepository pacienteRepository,
                         FisioterapeutaRepository fisioterapeutaRepository) {
        this.pacienteRepository = pacienteRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
    }

    public List<BuscaResultadoDTO> buscar(String termo, Integer usuarioId, String usuarioTipo) {
        String alvo = normalizar(termo);
        if (alvo.length() < 2) {
            return List.of();
        }

        List<BuscaResultadoDTO> resultados = new ArrayList<>();

        if ("CLINICA".equals(usuarioTipo)) {
            List<Fisioterapeuta> profissionais =
                    fisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(usuarioId);

            profissionais.stream()
                    .filter(f -> normalizar(f.getNome()).contains(alvo))
                    .forEach(f -> resultados.add(new BuscaResultadoDTO("PROFISSIONAL", f.getId(), f.getNome(),
                            f.getEspecialidade() == null ? "Profissional" : f.getEspecialidade(), f.getFoto())));

            profissionais.forEach(f -> pacienteRepository
                    .findByIdFisioterapeutaOrderByNomeAsc(f.getId()).stream()
                    .filter(p -> normalizar(p.getNome()).contains(alvo))
                    .forEach(p -> resultados.add(paraResultado(p, f.getNome()))));
        } else {
            pacienteRepository.findByIdFisioterapeutaOrderByNomeAsc(usuarioId).stream()
                    .filter(p -> normalizar(p.getNome()).contains(alvo))
                    .forEach(p -> resultados.add(paraResultado(p, null)));
        }

        return resultados.stream().limit(LIMITE).collect(Collectors.toList());
    }

    private BuscaResultadoDTO paraResultado(Paciente p, String nomeProfissional) {
        String detalhe = p.getSituacao() != null ? p.getSituacao()
                : nomeProfissional != null ? "com " + nomeProfissional
                : "Paciente";
        return new BuscaResultadoDTO("PACIENTE", p.getId(), p.getNome(), detalhe, p.getFoto());
    }

    /** Sem acento e em minúsculas, para "jose" achar "José". */
    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
    }
}
