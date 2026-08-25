package com.rehabit.service;

import com.rehabit.dto.DesempenhoDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import com.rehabit.security.PosseChecker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DesempenhoService {

    private static final String[] MESES_CURTOS = {
            "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"
    };
    private static final int MESES_NA_SERIE = 6;

    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final PacienteRepository pacienteRepository;
    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;

    public DesempenhoService(FisioterapeutaRepository fisioterapeutaRepository,
                              PacienteRepository pacienteRepository,
                              SessaoRepository sessaoRepository,
                              MedicaoRepository medicaoRepository) {
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.pacienteRepository = pacienteRepository;
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
    }

    public DesempenhoDTO doFisioterapeuta(Integer idFisioterapeuta, Integer usuarioId, String usuarioTipo) {
        Fisioterapeuta fisio = fisioterapeutaRepository.findById(idFisioterapeuta)
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.NOT_FOUND));
        PosseChecker.exigirDonoOuClinicaDona(fisio.getId(), fisio.getIdClinica(), usuarioId, usuarioTipo);

        List<Paciente> pacientes = pacienteRepository.findByIdFisioterapeutaOrderByNomeAsc(idFisioterapeuta);
        List<Sessao> sessoes = sessaoRepository.findByIdFisioterapeuta(idFisioterapeuta);

        DesempenhoDTO dto = new DesempenhoDTO();
        dto.setIdFisioterapeuta(fisio.getId());
        dto.setNomeFisioterapeuta(fisio.getNome());
        dto.setEspecialidade(fisio.getEspecialidade());
        dto.setFoto(fisio.getFoto());

        dto.setTotalPacientes(pacientes.size());
        dto.setPacientesAtivos(contarPorStatus(pacientes, "Ativo"));
        dto.setPacientesAlta(contarPorStatus(pacientes, "Alta"));
        dto.setTotalSessoes(sessoes.size());

        LocalDate limite = LocalDate.now().minusDays(30);
        dto.setSessoesUltimos30Dias(sessoes.stream()
                .filter(s -> s.getDataSessao() != null && !s.getDataSessao().isBefore(limite))
                .count());

        dto.setSessoesPorMes(serieMensal(sessoes));

        List<DesempenhoDTO.EvolucaoPacienteDTO> evolucoes = pacientes.stream()
                .map(this::evolucaoDoPaciente)
                .collect(Collectors.toList());
        dto.setPacientes(evolucoes);
        dto.setGanhoMedioGraus(mediaDosGanhos(evolucoes));

        return dto;
    }

    private long contarPorStatus(List<Paciente> pacientes, String status) {
        // Cadastros antigos podem estar sem status; "Ativo" é o padrão do cadastro.
        return pacientes.stream()
                .filter(p -> status.equals(p.getStatus() == null ? "Ativo" : p.getStatus()))
                .count();
    }

    /** Últimos meses de sessões, incluindo os meses sem nenhuma (senão o gráfico mente). */
    private List<DesempenhoDTO.PontoMensalDTO> serieMensal(List<Sessao> sessoes) {
        List<DesempenhoDTO.PontoMensalDTO> serie = new ArrayList<>();
        YearMonth atual = YearMonth.now();
        for (int i = MESES_NA_SERIE - 1; i >= 0; i--) {
            YearMonth mes = atual.minusMonths(i);
            long total = sessoes.stream()
                    .filter(s -> s.getDataSessao() != null && YearMonth.from(s.getDataSessao()).equals(mes))
                    .count();
            serie.add(new DesempenhoDTO.PontoMensalDTO(MESES_CURTOS[mes.getMonthValue() - 1], total));
        }
        return serie;
    }

    /**
     * Compara a primeira com a última medição do paciente. É o número que
     * mostra o benefício concreto do tratamento.
     */
    private DesempenhoDTO.EvolucaoPacienteDTO evolucaoDoPaciente(Paciente paciente) {
        DesempenhoDTO.EvolucaoPacienteDTO evo = new DesempenhoDTO.EvolucaoPacienteDTO();
        evo.setIdPaciente(paciente.getId());
        evo.setNome(paciente.getNome());
        evo.setStatus(paciente.getStatus() == null ? "Ativo" : paciente.getStatus());

        List<Sessao> sessoes = sessaoRepository.findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(paciente.getId());
        evo.setSessoes(sessoes.size());

        List<Sessao> cronologicas = sessoes.stream()
                .filter(s -> s.getDataSessao() != null)
                .sorted(Comparator.comparing(Sessao::getDataSessao))
                .collect(Collectors.toList());

        Double primeira = null;
        Double ultima = null;
        for (Sessao s : cronologicas) {
            Medicao m = medicaoRepository.findByIdSessao(s.getId());
            if (m == null || m.getAmplitudeMedia() == null) {
                continue;
            }
            double valor = m.getAmplitudeMedia().setScale(2, RoundingMode.HALF_UP).doubleValue();
            if (primeira == null) {
                primeira = valor;
            }
            ultima = valor;
        }

        evo.setAmplitudeInicial(primeira);
        evo.setAmplitudeAtual(ultima);
        // Com uma medição só não há evolução a mostrar — ganho fica nulo, não zero.
        if (primeira != null && ultima != null && cronologicas.size() > 1) {
            evo.setGanho(BigDecimal.valueOf(ultima - primeira).setScale(1, RoundingMode.HALF_UP).doubleValue());
        }
        return evo;
    }

    private Double mediaDosGanhos(List<DesempenhoDTO.EvolucaoPacienteDTO> evolucoes) {
        List<Double> ganhos = evolucoes.stream()
                .map(DesempenhoDTO.EvolucaoPacienteDTO::getGanho)
                .filter(g -> g != null)
                .collect(Collectors.toList());
        if (ganhos.isEmpty()) {
            return null;
        }
        double soma = ganhos.stream().mapToDouble(Double::doubleValue).sum();
        return BigDecimal.valueOf(soma / ganhos.size()).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
