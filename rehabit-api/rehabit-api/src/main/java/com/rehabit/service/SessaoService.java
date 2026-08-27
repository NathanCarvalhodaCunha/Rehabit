package com.rehabit.service;

import com.rehabit.dto.SessaoCreateDTO;
import com.rehabit.dto.SessaoDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Medicao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.SessaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SessaoService {

    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;
    private final PacienteService pacienteService;
    private final NotificacaoService notificacaoService;

    public SessaoService(SessaoRepository sessaoRepository, MedicaoRepository medicaoRepository,
                          PacienteService pacienteService, NotificacaoService notificacaoService) {
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
        this.pacienteService = pacienteService;
        this.notificacaoService = notificacaoService;
    }

    @Transactional
    public SessaoDTO cadastrar(Integer idPaciente, SessaoCreateDTO dados, Integer usuarioId, String usuarioTipo) {
        Paciente paciente = pacienteService.carregarComPosse(idPaciente, usuarioId, usuarioTipo);

        // A sessão é o registro de um atendimento que já aconteceu; datar no
        // futuro estragaria a evolução do paciente e os números do painel.
        if (dados.getData() != null && dados.getData().isAfter(LocalDate.now())) {
            throw new AuthException(
                    "A sessão não pode ter uma data futura. Use a Agenda para marcar o que ainda vai acontecer.",
                    HttpStatus.BAD_REQUEST);
        }

        Sessao sessao = new Sessao();
        sessao.setDataSessao(dados.getData());
        sessao.setHoraSessao(LocalTime.now().withNano(0));
        sessao.setDuracao(dados.getDuracao());
        sessao.setIdFisioterapeuta(paciente.getIdFisioterapeuta());
        sessao.setIdPaciente(paciente.getId());
        sessao.setObservacoes(vazioParaNulo(dados.getObservacoes()));
        sessao.setDor(dorValida(dados.getDor()));
        Sessao sessaoSalva = sessaoRepository.save(sessao);

        Medicao medicao = new Medicao();
        medicao.setAmplitudeMedia(dados.getAmplitudeMedia());
        medicao.setDataMedicao(dados.getData());
        medicao.setHoraMedicao(LocalTime.now().withNano(0));
        medicao.setIdSessao(sessaoSalva.getId());
        Medicao medicaoSalva = medicaoRepository.save(medicao);
        notificacaoService.criar(paciente.getIdClinica(), "NOVA_SESSAO",
                "Nova sessão registrada para " + paciente.getNome());

        SessaoDTO dto = new SessaoDTO(sessaoSalva.getId(), sessaoSalva.getDataSessao(), sessaoSalva.getDuracao(),
                medicaoSalva.getAmplitudeMedia(), sessaoSalva.getObservacoes());
        dto.setDor(sessaoSalva.getDor());
        return dto;
    }

    /** Fora de 0–10 a escala não significa nada, então guarda nulo. */
    private Integer dorValida(Integer dor) {
        if (dor == null || dor < 0 || dor > 10) {
            return null;
        }
        return dor;
    }

    public List<SessaoDTO> listarPorPaciente(Integer idPaciente, Integer usuarioId, String usuarioTipo) {
        pacienteService.carregarComPosse(idPaciente, usuarioId, usuarioTipo);
        return sessaoRepository.findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(idPaciente).stream()
                .map(s -> {
                    Medicao medicao = medicaoRepository.findByIdSessao(s.getId());
                    SessaoDTO dto = new SessaoDTO(s.getId(), s.getDataSessao(), s.getDuracao(),
                            medicao != null ? medicao.getAmplitudeMedia() : null, s.getObservacoes());
                    dto.setDor(s.getDor());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
