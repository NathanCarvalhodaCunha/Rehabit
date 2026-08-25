package com.rehabit.service;

import com.rehabit.dto.AgendamentoCreateDTO;
import com.rehabit.dto.AgendamentoDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Agendamento;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import com.rehabit.repository.AgendamentoRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.security.PosseChecker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final PacienteRepository pacienteRepository;
    private final PacienteService pacienteService;
    private final ConfiguracaoService configuracaoService;

    public AgendamentoService(AgendamentoRepository agendamentoRepository,
                               FisioterapeutaRepository fisioterapeutaRepository,
                               PacienteRepository pacienteRepository,
                               PacienteService pacienteService,
                               ConfiguracaoService configuracaoService) {
        this.agendamentoRepository = agendamentoRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.pacienteRepository = pacienteRepository;
        this.pacienteService = pacienteService;
        this.configuracaoService = configuracaoService;
    }

    @Transactional
    public AgendamentoDTO agendar(AgendamentoCreateDTO dados, Integer usuarioId, String usuarioTipo) {
        if (!"FISIOTERAPEUTA".equals(usuarioTipo)) {
            throw new AuthException("Apenas um profissional logado pode criar agendamentos.", HttpStatus.FORBIDDEN);
        }
        Paciente paciente = pacienteService.carregarComPosse(dados.getIdPaciente(), usuarioId, usuarioTipo);
        exigirHorarioLivre(usuarioId, usuarioTipo, dados);

        Agendamento agendamento = new Agendamento();
        agendamento.setDataAgendamento(dados.getData());
        agendamento.setHoraAgendamento(dados.getHora());
        agendamento.setObservacao(vazioParaNulo(dados.getObservacao()));
        agendamento.setIdFisioterapeuta(usuarioId);
        agendamento.setIdPaciente(paciente.getId());

        Agendamento salvo = agendamentoRepository.save(agendamento);
        return paraDTO(salvo, paciente.getNome());
    }

    public List<AgendamentoDTO> listarProximos(Integer idFisioterapeuta, Integer usuarioId, String usuarioTipo) {
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(idFisioterapeuta)
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.BAD_REQUEST));
        PosseChecker.exigirDonoOuClinicaDona(fisioterapeuta.getId(), fisioterapeuta.getIdClinica(), usuarioId, usuarioTipo);

        return agendamentoRepository
                .findByIdFisioterapeutaAndDataAgendamentoGreaterThanEqualOrderByDataAgendamentoAscHoraAgendamentoAsc(
                        idFisioterapeuta, LocalDate.now())
                .stream()
                .map(a -> paraDTO(a, nomeDoPaciente(a.getIdPaciente())))
                .collect(Collectors.toList());
    }

    /**
     * Agenda da clínica inteira, somando todos os seus profissionais.
     * {@code apenasFuturos} separa o card "Próximas consultas" da tela de
     * histórico, que precisa também do que já passou.
     */
    public List<AgendamentoDTO> listarDaClinica(Integer idClinica, boolean apenasFuturos,
                                                 Integer usuarioId, String usuarioTipo) {
        PosseChecker.exigirClinicaDona(idClinica, usuarioId, usuarioTipo);

        List<Fisioterapeuta> profissionais = fisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(idClinica);
        if (profissionais.isEmpty()) {
            return List.of();
        }
        Map<Integer, String> nomePorId = profissionais.stream()
                .collect(Collectors.toMap(Fisioterapeuta::getId, Fisioterapeuta::getNome));
        List<Integer> ids = new ArrayList<>(nomePorId.keySet());

        List<Agendamento> agendamentos = apenasFuturos
                ? agendamentoRepository
                        .findByIdFisioterapeutaInAndDataAgendamentoBetweenOrderByDataAgendamentoAscHoraAgendamentoAsc(
                                ids, LocalDate.now(), LocalDate.now().plusYears(5))
                : agendamentoRepository.findByIdFisioterapeutaInOrderByDataAgendamentoDescHoraAgendamentoDesc(ids);

        return agendamentos.stream()
                .map(a -> new AgendamentoDTO(a.getId(), a.getDataAgendamento(), a.getHoraAgendamento(),
                        a.getObservacao(), a.getIdPaciente(), nomeDoPaciente(a.getIdPaciente()),
                        nomePorId.get(a.getIdFisioterapeuta())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelar(Integer id, Integer usuarioId, String usuarioTipo) {
        Agendamento agendamento = agendamentoRepository.findById(id)
                .orElseThrow(() -> new AuthException("Agendamento não encontrado.", HttpStatus.NOT_FOUND));
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(agendamento.getIdFisioterapeuta())
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.BAD_REQUEST));
        PosseChecker.exigirDonoOuClinicaDona(fisioterapeuta.getId(), fisioterapeuta.getIdClinica(), usuarioId, usuarioTipo);
        agendamentoRepository.delete(agendamento);
    }

    /**
     * Recusa dois atendimentos sobrepostos do mesmo profissional, usando a
     * duração padrão configurada como tamanho de cada sessão. Só age se o
     * aviso de conflito estiver ligado nas configurações.
     */
    private void exigirHorarioLivre(Integer idFisioterapeuta, String usuarioTipo, AgendamentoCreateDTO dados) {
        if (!configuracaoService.deveAvisarConflito(usuarioTipo, idFisioterapeuta)) {
            return;
        }
        int duracao = configuracaoService.duracaoPadrao(usuarioTipo, idFisioterapeuta);
        LocalTime inicioNovo = dados.getHora();
        LocalTime fimNovo = inicioNovo.plusMinutes(duracao);

        boolean conflita = agendamentoRepository
                .findByIdFisioterapeutaAndDataAgendamento(idFisioterapeuta, dados.getData())
                .stream()
                .anyMatch(existente -> {
                    LocalTime inicioExistente = existente.getHoraAgendamento();
                    LocalTime fimExistente = inicioExistente.plusMinutes(duracao);
                    return inicioNovo.isBefore(fimExistente) && inicioExistente.isBefore(fimNovo);
                });

        if (conflita) {
            throw new AuthException(
                    "Já existe uma consulta nesse horário. Escolha outro ou desligue o aviso de conflito nas configurações.",
                    HttpStatus.CONFLICT);
        }
    }

    private String nomeDoPaciente(Integer idPaciente) {
        return pacienteRepository.findById(idPaciente).map(Paciente::getNome).orElse(null);
    }

    private AgendamentoDTO paraDTO(Agendamento a, String nomePaciente) {
        return new AgendamentoDTO(a.getId(), a.getDataAgendamento(), a.getHoraAgendamento(), a.getObservacao(),
                a.getIdPaciente(), nomePaciente);
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
