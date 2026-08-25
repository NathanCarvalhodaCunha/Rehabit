package com.rehabit.service;

import com.rehabit.dto.EstatisticaCardDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Sessao;
import com.rehabit.repository.AgendamentoRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EstatisticasService {

    private final AgendamentoRepository agendamentoRepository;
    private final SessaoRepository sessaoRepository;
    private final PacienteRepository pacienteRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final MedicaoRepository medicaoRepository;

    public EstatisticasService(AgendamentoRepository agendamentoRepository,
                                SessaoRepository sessaoRepository,
                                PacienteRepository pacienteRepository,
                                FisioterapeutaRepository fisioterapeutaRepository,
                                MedicaoRepository medicaoRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.sessaoRepository = sessaoRepository;
        this.pacienteRepository = pacienteRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.medicaoRepository = medicaoRepository;
    }

    public List<EstatisticaCardDTO> resumo(Integer usuarioId, String usuarioTipo) {
        if (usuarioId == null || usuarioTipo == null) {
            throw new AuthException("Sessão inválida.", HttpStatus.UNAUTHORIZED);
        }
        return "CLINICA".equals(usuarioTipo) ? resumoClinica(usuarioId) : resumoProfissional(usuarioId);
    }

    private List<EstatisticaCardDTO> resumoProfissional(Integer idFisioterapeuta) {
        List<Integer> ids = List.of(idFisioterapeuta);
        List<EstatisticaCardDTO> cards = new ArrayList<>();
        cards.add(cardConsultasSemana(ids));
        cards.add(new EstatisticaCardDTO("Pacientes ativos",
                String.valueOf(pacienteRepository.countByIdFisioterapeutaAndStatus(idFisioterapeuta, "Ativo")),
                "de " + pacienteRepository.countByIdFisioterapeuta(idFisioterapeuta) + " no total"));
        cards.add(cardSessoesMes(ids));
        cards.add(cardAmplitude(ids));
        return cards;
    }

    private List<EstatisticaCardDTO> resumoClinica(Integer idClinica) {
        List<Integer> ids = fisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(idClinica).stream()
                .map(Fisioterapeuta::getId)
                .collect(Collectors.toList());
        List<EstatisticaCardDTO> cards = new ArrayList<>();
        cards.add(cardConsultasSemana(ids));
        cards.add(new EstatisticaCardDTO("Pacientes ativos",
                String.valueOf(pacienteRepository.countByIdClinicaAndStatus(idClinica, "Ativo")),
                "de " + pacienteRepository.countByIdClinica(idClinica) + " no total"));
        cards.add(cardSessoesMes(ids));
        cards.add(new EstatisticaCardDTO("Profissionais",
                String.valueOf(fisioterapeutaRepository.countByIdClinica(idClinica)),
                "vinculados à instituição"));
        return cards;
    }

    private EstatisticaCardDTO cardConsultasSemana(List<Integer> ids) {
        if (ids.isEmpty()) {
            return new EstatisticaCardDTO("Consultas esta semana", "0", "nenhuma agendada");
        }
        LocalDate hoje = LocalDate.now();
        LocalDate segunda = hoje.with(DayOfWeek.MONDAY);
        LocalDate domingo = hoje.with(DayOfWeek.SUNDAY);
        long total = agendamentoRepository
                .findByIdFisioterapeutaInAndDataAgendamentoBetweenOrderByDataAgendamentoAscHoraAgendamentoAsc(
                        ids, segunda, domingo)
                .size();
        long restantes = agendamentoRepository
                .findByIdFisioterapeutaInAndDataAgendamentoBetweenOrderByDataAgendamentoAscHoraAgendamentoAsc(
                        ids, hoje, domingo)
                .size();
        return new EstatisticaCardDTO("Consultas esta semana", String.valueOf(total),
                restantes + " ainda por vir");
    }

    private EstatisticaCardDTO cardSessoesMes(List<Integer> ids) {
        if (ids.isEmpty()) {
            return new EstatisticaCardDTO("Sessões no mês", "0", "nenhuma registrada");
        }
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        int totalMes = sessaoRepository.findByIdFisioterapeutaInAndDataSessaoBetween(ids, inicioMes, hoje).size();

        LocalDate inicioMesPassado = inicioMes.minusMonths(1);
        LocalDate fimMesPassado = inicioMes.minusDays(1);
        int totalMesPassado = sessaoRepository
                .findByIdFisioterapeutaInAndDataSessaoBetween(ids, inicioMesPassado, fimMesPassado).size();

        String detalhe = totalMesPassado == 0
                ? "sem base do mês anterior"
                : formatarVariacao(totalMes - totalMesPassado) + " vs. mês anterior";
        return new EstatisticaCardDTO("Sessões no mês", String.valueOf(totalMes), detalhe);
    }

    /**
     * Amplitude média das medições do mês corrente, comparada com a do mês
     * anterior — é o indicador que mostra se os pacientes estão evoluindo.
     */
    private EstatisticaCardDTO cardAmplitude(List<Integer> ids) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        BigDecimal mediaAtual = mediaAmplitude(ids, inicioMes, hoje);
        if (mediaAtual == null) {
            return new EstatisticaCardDTO("Amplitude média", "-", "sem medições no mês");
        }
        BigDecimal mediaAnterior = mediaAmplitude(ids, inicioMes.minusMonths(1), inicioMes.minusDays(1));
        String detalhe = mediaAnterior == null
                ? "sem base do mês anterior"
                : formatarVariacao(mediaAtual.subtract(mediaAnterior).setScale(0, RoundingMode.HALF_UP).intValue())
                        + "° vs. mês anterior";
        return new EstatisticaCardDTO("Amplitude média", mediaAtual.setScale(0, RoundingMode.HALF_UP) + "°", detalhe);
    }

    private BigDecimal mediaAmplitude(List<Integer> ids, LocalDate inicio, LocalDate fim) {
        if (ids.isEmpty()) {
            return null;
        }
        List<Sessao> sessoes = sessaoRepository.findByIdFisioterapeutaInAndDataSessaoBetween(ids, inicio, fim);
        List<BigDecimal> amplitudes = new ArrayList<>();
        for (Sessao s : sessoes) {
            Medicao m = medicaoRepository.findByIdSessao(s.getId());
            if (m != null && m.getAmplitudeMedia() != null) {
                amplitudes.add(m.getAmplitudeMedia());
            }
        }
        if (amplitudes.isEmpty()) {
            return null;
        }
        BigDecimal soma = amplitudes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return soma.divide(BigDecimal.valueOf(amplitudes.size()), 2, RoundingMode.HALF_UP);
    }

    private String formatarVariacao(int diferenca) {
        return (diferenca >= 0 ? "+" : "") + diferenca;
    }
}
