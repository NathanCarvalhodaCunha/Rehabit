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
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final PacienteRepository pacienteRepository;
    private final PacienteService pacienteService;

    public AgendamentoService(AgendamentoRepository agendamentoRepository,
                               FisioterapeutaRepository fisioterapeutaRepository,
                               PacienteRepository pacienteRepository,
                               PacienteService pacienteService) {
        this.agendamentoRepository = agendamentoRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.pacienteRepository = pacienteRepository;
        this.pacienteService = pacienteService;
    }

    @Transactional
    public AgendamentoDTO agendar(AgendamentoCreateDTO dados, Integer usuarioId, String usuarioTipo) {
        if (!"FISIOTERAPEUTA".equals(usuarioTipo)) {
            throw new AuthException("Apenas um profissional logado pode criar agendamentos.", HttpStatus.FORBIDDEN);
        }
        Paciente paciente = pacienteService.carregarComPosse(dados.getIdPaciente(), usuarioId, usuarioTipo);

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

    @Transactional
    public void cancelar(Integer id, Integer usuarioId, String usuarioTipo) {
        Agendamento agendamento = agendamentoRepository.findById(id)
                .orElseThrow(() -> new AuthException("Agendamento não encontrado.", HttpStatus.NOT_FOUND));
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(agendamento.getIdFisioterapeuta())
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.BAD_REQUEST));
        PosseChecker.exigirDonoOuClinicaDona(fisioterapeuta.getId(), fisioterapeuta.getIdClinica(), usuarioId, usuarioTipo);
        agendamentoRepository.delete(agendamento);
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
