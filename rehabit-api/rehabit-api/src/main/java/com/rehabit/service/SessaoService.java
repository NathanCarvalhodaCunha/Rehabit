package com.rehabit.service;

import com.rehabit.config.RelogioConfig;
import com.rehabit.dto.SessaoCreateDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.dto.SessaoDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Medicao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.SessaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SessaoService {

    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;
    private final PacienteService pacienteService;
    private final NotificacaoService notificacaoService;
    private final GoniometroService goniometroService;
    private final Clock relogio;

    public SessaoService(SessaoRepository sessaoRepository, MedicaoRepository medicaoRepository,
                          PacienteService pacienteService, NotificacaoService notificacaoService,
                          GoniometroService goniometroService, Clock relogio) {
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
        this.pacienteService = pacienteService;
        this.notificacaoService = notificacaoService;
        this.goniometroService = goniometroService;
        this.relogio = relogio;
    }

    @Transactional
    public SessaoDTO cadastrar(Integer idPaciente, SessaoCreateDTO dados, Integer usuarioId, String usuarioTipo) {
        Paciente paciente = pacienteService.carregarComPosse(idPaciente, usuarioId, usuarioTipo);

        // A sessão é registrada enquanto acontece, então o momento do registro
        // é o momento da sessão. Quem carimba é o servidor: uma data digitada
        // (ou o relógio errado do computador da clínica) deslocaria a evolução
        // do paciente e os números do painel. A sessão e a sua medição levam
        // o mesmo instante, lido uma vez só.
        LocalDateTime agora = LocalDateTime.now(relogio.withZone(RelogioConfig.FUSO)).withNano(0);

        Sessao sessao = new Sessao();
        sessao.setDataSessao(agora.toLocalDate());
        sessao.setHoraSessao(agora.toLocalTime());
        sessao.setDuracao(dados.getDuracao());
        sessao.setIdFisioterapeuta(paciente.getIdFisioterapeuta());
        sessao.setIdPaciente(paciente.getId());
        sessao.setObservacoes(vazioParaNulo(dados.getObservacoes()));
        sessao.setDor(dorValida(dados.getDor()));
        Sessao sessaoSalva = sessaoRepository.save(sessao);

        Medicao medicao = new Medicao();
        medicao.setAmplitudeMedia(dados.getAmplitudeMedia());
        medicao.setDataMedicao(agora.toLocalDate());
        medicao.setHoraMedicao(agora.toLocalTime());
        medicao.setIdSessao(sessaoSalva.getId());
        // A curva vem do estado ao vivo, não do navegador: são centenas de
        // pontos que o servidor já tem, e assim ninguém consegue inventar uma.
        medicao.setCurva(goniometroService.curvaDaCaptura(
                paciente.getIdClinica(), dados.getCapturaIniciadaEm()));
        Medicao medicaoSalva = medicaoRepository.save(medicao);
        notificacaoService.criar(paciente.getIdClinica(), "NOVA_SESSAO",
                "Nova sessão registrada para " + paciente.getNome());

        SessaoDTO dto = new SessaoDTO(sessaoSalva.getId(), sessaoSalva.getDataSessao(), sessaoSalva.getDuracao(),
                medicaoSalva.getAmplitudeMedia(), sessaoSalva.getObservacoes());
        dto.setHora(sessaoSalva.getHoraSessao());
        dto.setDor(sessaoSalva.getDor());
        dto.setTemCurva(medicaoSalva.getCurva() != null);
        return dto;
    }

    /** Fora de 0–10 a escala não significa nada, então guarda nulo. */
    private Integer dorValida(Integer dor) {
        if (dor == null || dor < 0 || dor > 10) {
            return null;
        }
        return dor;
    }

    /**
     * A curva do movimento de uma sessão, como veio da captura. Fica fora da
     * listagem de propósito: são centenas de pontos por sessão, e só interessam
     * quando alguém abre aquela sessão específica.
     */
    public String buscarCurva(Integer idPaciente, Integer idSessao, Integer usuarioId, String usuarioTipo) {
        pacienteService.carregarComPosse(idPaciente, usuarioId, usuarioTipo);
        Sessao sessao = sessaoRepository.findById(idSessao)
                .orElseThrow(() -> new AuthException("Sessão não encontrada.", HttpStatus.NOT_FOUND));
        // Sem esta checagem, a posse do paciente da URL liberaria a sessão de
        // qualquer outro paciente, bastando trocar o id na barra de endereço.
        if (!idPaciente.equals(sessao.getIdPaciente())) {
            throw new AuthException("Sessão não encontrada.", HttpStatus.NOT_FOUND);
        }
        Medicao medicao = medicaoRepository.findByIdSessao(idSessao);
        if (medicao == null || medicao.getCurva() == null) {
            throw new AuthException("Esta sessão não tem curva gravada.", HttpStatus.NOT_FOUND);
        }
        return medicao.getCurva();
    }

    public List<SessaoDTO> listarPorPaciente(Integer idPaciente, Integer usuarioId, String usuarioTipo) {
        pacienteService.carregarComPosse(idPaciente, usuarioId, usuarioTipo);
        return sessaoRepository.findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(idPaciente).stream()
                .map(s -> {
                    Medicao medicao = medicaoRepository.findByIdSessao(s.getId());
                    SessaoDTO dto = new SessaoDTO(s.getId(), s.getDataSessao(), s.getDuracao(),
                            medicao != null ? medicao.getAmplitudeMedia() : null, s.getObservacoes());
                    dto.setHora(s.getHoraSessao());
                    dto.setDor(s.getDor());
                    dto.setTemCurva(medicao != null && medicao.getCurva() != null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
