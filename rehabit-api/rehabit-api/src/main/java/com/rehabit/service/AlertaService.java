package com.rehabit.service;

import com.rehabit.dto.AlertaDTO;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Varre os pacientes e levanta o que merece atenção do profissional:
 * sumiço, piora entre medições e metas atingidas.
 */
@Service
public class AlertaService {

    private static final int DIAS_SEM_SESSAO = 21;

    private final PacienteRepository pacienteRepository;
    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;

    public AlertaService(PacienteRepository pacienteRepository,
                          SessaoRepository sessaoRepository,
                          MedicaoRepository medicaoRepository,
                          FisioterapeutaRepository fisioterapeutaRepository) {
        this.pacienteRepository = pacienteRepository;
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
    }

    public List<AlertaDTO> listar(Integer usuarioId, String usuarioTipo) {
        List<Paciente> pacientes = "CLINICA".equals(usuarioTipo)
                ? fisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(usuarioId).stream()
                        .map(Fisioterapeuta::getId)
                        .flatMap(id -> pacienteRepository.findByIdFisioterapeutaOrderByNomeAsc(id).stream())
                        .collect(Collectors.toList())
                : pacienteRepository.findByIdFisioterapeutaOrderByNomeAsc(usuarioId);

        List<AlertaDTO> alertas = new ArrayList<>();
        for (Paciente p : pacientes) {
            // Quem recebeu alta ou está inativo não deve gerar cobrança.
            if (!"Ativo".equals(p.getStatus() == null ? "Ativo" : p.getStatus())) {
                continue;
            }
            List<Sessao> sessoes = sessaoRepository
                    .findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(p.getId());

            alertaDeSumico(p, sessoes).ifPresent(alertas::add);
            alertaDeRegressao(p, sessoes).ifPresent(alertas::add);
            alertaDeMeta(p, sessoes).ifPresent(alertas::add);
        }

        // Atenção primeiro; conquistas depois.
        alertas.sort(Comparator.comparing((AlertaDTO a) -> "BOM".equals(a.getNivel()) ? 1 : 0));
        return alertas;
    }

    private java.util.Optional<AlertaDTO> alertaDeSumico(Paciente p, List<Sessao> sessoes) {
        LocalDate ultima = sessoes.stream()
                .map(Sessao::getDataSessao)
                .filter(d -> d != null)
                .max(LocalDate::compareTo)
                .orElse(null);

        if (ultima == null) {
            return java.util.Optional.of(new AlertaDTO("ATENCAO", "Sem nenhuma sessão",
                    p.getNome() + " está cadastrado mas ainda não teve nenhuma sessão registrada.",
                    p.getId(), p.getNome()));
        }
        long dias = ChronoUnit.DAYS.between(ultima, LocalDate.now());
        if (dias >= DIAS_SEM_SESSAO) {
            return java.util.Optional.of(new AlertaDTO("ATENCAO", "Paciente sumido",
                    p.getNome() + " está há " + dias + " dias sem sessão.", p.getId(), p.getNome()));
        }
        return java.util.Optional.empty();
    }

    /** Duas quedas seguidas de amplitude indicam que algo mudou para pior. */
    private java.util.Optional<AlertaDTO> alertaDeRegressao(Paciente p, List<Sessao> sessoes) {
        List<BigDecimal> ultimas = sessoes.stream()
                .limit(3)
                .map(s -> medicaoRepository.findByIdSessao(s.getId()))
                .filter(m -> m != null && m.getAmplitudeMedia() != null)
                .map(Medicao::getAmplitudeMedia)
                .collect(Collectors.toList());

        if (ultimas.size() < 3) {
            return java.util.Optional.empty();
        }
        // A lista vem da mais recente para a mais antiga.
        boolean caiuDuasVezes = ultimas.get(0).compareTo(ultimas.get(1)) < 0
                && ultimas.get(1).compareTo(ultimas.get(2)) < 0;
        if (!caiuDuasVezes) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new AlertaDTO("ATENCAO", "Regressão na amplitude",
                p.getNome() + " caiu de " + ultimas.get(2) + "° para " + ultimas.get(0) + "° nas últimas medições.",
                p.getId(), p.getNome()));
    }

    private java.util.Optional<AlertaDTO> alertaDeMeta(Paciente p, List<Sessao> sessoes) {
        if (p.getMetaAmplitude() == null) {
            return java.util.Optional.empty();
        }
        BigDecimal atual = sessoes.stream()
                .map(s -> medicaoRepository.findByIdSessao(s.getId()))
                .filter(m -> m != null && m.getAmplitudeMedia() != null)
                .map(Medicao::getAmplitudeMedia)
                .findFirst()
                .orElse(null);
        if (atual == null) {
            return java.util.Optional.empty();
        }
        if (atual.compareTo(p.getMetaAmplitude()) >= 0) {
            return java.util.Optional.of(new AlertaDTO("BOM", "Meta atingida",
                    p.getNome() + " alcançou " + atual + "°, a meta era " + p.getMetaAmplitude() + "°.",
                    p.getId(), p.getNome()));
        }
        if (p.getMetaData() != null && LocalDate.now().isAfter(p.getMetaData())) {
            return java.util.Optional.of(new AlertaDTO("ATENCAO", "Meta vencida",
                    p.getNome() + " está em " + atual + "° e a meta de " + p.getMetaAmplitude()
                            + "° venceu em " + p.getMetaData() + ".", p.getId(), p.getNome()));
        }
        return java.util.Optional.empty();
    }
}
