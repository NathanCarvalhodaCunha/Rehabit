package com.rehabit.service;

import com.rehabit.dto.AgendamentoCreateDTO;
import com.rehabit.dto.AgendamentoDTO;
import com.rehabit.dto.ConfiguracaoDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Agendamento;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import com.rehabit.repository.AgendamentoRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import com.rehabit.security.PosseChecker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AgendamentoService {

    private static final DateTimeFormatter HORA_BR = DateTimeFormatter.ofPattern("HH:mm");

    private final AgendamentoRepository agendamentoRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final PacienteRepository pacienteRepository;
    private final PacienteService pacienteService;
    private final ConfiguracaoService configuracaoService;
    private final SessaoRepository sessaoRepository;

    public AgendamentoService(AgendamentoRepository agendamentoRepository,
                               FisioterapeutaRepository fisioterapeutaRepository,
                               PacienteRepository pacienteRepository,
                               PacienteService pacienteService,
                               ConfiguracaoService configuracaoService,
                               SessaoRepository sessaoRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.pacienteRepository = pacienteRepository;
        this.pacienteService = pacienteService;
        this.configuracaoService = configuracaoService;
        this.sessaoRepository = sessaoRepository;
    }

    @Transactional
    public AgendamentoDTO agendar(AgendamentoCreateDTO dados, Integer usuarioId, String usuarioTipo) {
        if (!"FISIOTERAPEUTA".equals(usuarioTipo)) {
            throw new AuthException("Apenas um profissional logado pode criar agendamentos.", HttpStatus.FORBIDDEN);
        }
        Paciente paciente = pacienteService.carregarComPosse(dados.getIdPaciente(), usuarioId, usuarioTipo);
        exigirDataFutura(dados.getData(), dados.getHora());
        exigirDentroDoExpediente(usuarioId, dados.getData(), dados.getHora());
        exigirHorarioLivre(usuarioId, dados.getData(), dados.getHora(), null);

        Agendamento agendamento = new Agendamento();
        agendamento.setDataAgendamento(dados.getData());
        agendamento.setHoraAgendamento(dados.getHora());
        agendamento.setObservacao(vazioParaNulo(dados.getObservacao()));
        agendamento.setIdFisioterapeuta(usuarioId);
        agendamento.setIdPaciente(paciente.getId());
        agendamento.setStatus("AGENDADA");

        Agendamento salvo = agendamentoRepository.save(agendamento);
        return paraDTO(salvo, paciente.getNome());
    }

    /**
     * Move uma consulta para outra data/hora. O registro é o mesmo — só a
     * data muda —, então presença, observação e vínculo com o paciente
     * continuam valendo; a data anterior fica guardada para a agenda poder
     * mostrar de onde a consulta veio.
     */
    @Transactional
    public AgendamentoDTO remarcar(Integer id, AgendamentoCreateDTO dados, Integer usuarioId, String usuarioTipo) {
        Agendamento agendamento = carregarComPosse(id, usuarioId, usuarioTipo);

        if (dados.getData() == null || dados.getHora() == null) {
            throw new AuthException("Informe a nova data e o novo horário.", HttpStatus.BAD_REQUEST);
        }
        if (dados.getData().equals(agendamento.getDataAgendamento())
                && dados.getHora().equals(agendamento.getHoraAgendamento())) {
            throw new AuthException("A nova data e horário são os mesmos da consulta atual.",
                    HttpStatus.BAD_REQUEST);
        }

        exigirQueAindaNaoTenhaAcontecido(agendamento);

        Integer idFisioterapeuta = agendamento.getIdFisioterapeuta();
        exigirDataFutura(dados.getData(), dados.getHora());
        exigirDentroDoExpediente(idFisioterapeuta, dados.getData(), dados.getHora());
        exigirHorarioLivre(idFisioterapeuta, dados.getData(), dados.getHora(), id);

        // Só a primeira remarcação grava a origem: o que interessa é de onde a
        // consulta saiu, não cada passo intermediário.
        if (agendamento.getDataOriginal() == null) {
            agendamento.setDataOriginal(agendamento.getDataAgendamento());
            agendamento.setHoraOriginal(agendamento.getHoraAgendamento());
        }
        agendamento.setDataAgendamento(dados.getData());
        agendamento.setHoraAgendamento(dados.getHora());
        if (dados.getObservacao() != null) {
            agendamento.setObservacao(vazioParaNulo(dados.getObservacao()));
        }
        agendamento.setStatus("REMARCADA");

        Agendamento salvo = agendamentoRepository.save(agendamento);
        return paraDTO(salvo, nomeDoPaciente(salvo.getIdPaciente()));
    }

    public List<AgendamentoDTO> listarProximos(Integer idFisioterapeuta, Integer usuarioId, String usuarioTipo) {
        exigirAcessoAoProfissional(idFisioterapeuta, usuarioId, usuarioTipo);

        return agendamentoRepository
                .findByIdFisioterapeutaAndDataAgendamentoGreaterThanEqualOrderByDataAgendamentoAscHoraAgendamentoAsc(
                        idFisioterapeuta, LocalDate.now())
                .stream()
                .map(a -> paraDTO(a, nomeDoPaciente(a.getIdPaciente())))
                .collect(Collectors.toList());
    }

    /**
     * Consultas do profissional que já passaram, da mais recente para a mais
     * antiga. O corte é por dia inteiro: a agenda do dia continua na lista de
     * próximos até a virada, para o profissional ainda conseguir marcar
     * presença nela.
     */
    public List<AgendamentoDTO> listarHistorico(Integer idFisioterapeuta, Integer usuarioId, String usuarioTipo) {
        exigirAcessoAoProfissional(idFisioterapeuta, usuarioId, usuarioTipo);

        return agendamentoRepository
                .findByIdFisioterapeutaAndDataAgendamentoLessThanOrderByDataAgendamentoDescHoraAgendamentoDesc(
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
                .map(a -> paraDTO(a, nomeDoPaciente(a.getIdPaciente()),
                        nomePorId.get(a.getIdFisioterapeuta())))
                .collect(Collectors.toList());
    }

    /**
     * Sessões já realizadas na clínica inteira. Reaproveita o AgendamentoDTO:
     * a tela de consultas mostra realizadas e agendadas lado a lado, com os
     * mesmos campos (data, hora, paciente, profissional).
     */
    public List<AgendamentoDTO> listarRealizadasDaClinica(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        PosseChecker.exigirClinicaDona(idClinica, usuarioId, usuarioTipo);

        List<Fisioterapeuta> profissionais = fisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(idClinica);
        if (profissionais.isEmpty()) {
            return List.of();
        }
        Map<Integer, String> nomePorId = profissionais.stream()
                .collect(Collectors.toMap(Fisioterapeuta::getId, Fisioterapeuta::getNome));

        return sessaoRepository
                .findByIdFisioterapeutaInOrderByDataSessaoDescHoraSessaoDesc(new ArrayList<>(nomePorId.keySet()))
                .stream()
                .map(s -> sessaoParaDTO(s.getId(), s.getDataSessao(), s.getHoraSessao(), s.getDuracao(),
                        s.getIdPaciente(), s.getIdFisioterapeuta(), nomePorId.get(s.getIdFisioterapeuta())))
                .collect(Collectors.toList());
    }

    /** Mesma lista de sessões realizadas, mas de um único profissional. */
    public List<AgendamentoDTO> listarRealizadasDoProfissional(Integer idFisioterapeuta, Integer usuarioId,
                                                                 String usuarioTipo) {
        Fisioterapeuta fisioterapeuta = exigirAcessoAoProfissional(idFisioterapeuta, usuarioId, usuarioTipo);

        return sessaoRepository.findByIdFisioterapeutaOrderByDataSessaoDescHoraSessaoDesc(idFisioterapeuta)
                .stream()
                .map(s -> sessaoParaDTO(s.getId(), s.getDataSessao(), s.getHoraSessao(), s.getDuracao(),
                        s.getIdPaciente(), s.getIdFisioterapeuta(), fisioterapeuta.getNome()))
                .collect(Collectors.toList());
    }

    private static final Set<String> STATUS_VALIDOS =
            Set.of("AGENDADA", "REALIZADA", "FALTOU", "REMARCADA");

    /** Marca presença/falta de um agendamento. */
    @Transactional
    public AgendamentoDTO alterarStatus(Integer id, String novoStatus, Integer usuarioId, String usuarioTipo) {
        if (novoStatus == null || !STATUS_VALIDOS.contains(novoStatus)) {
            throw new AuthException("Status inválido.", HttpStatus.BAD_REQUEST);
        }
        Agendamento agendamento = carregarComPosse(id, usuarioId, usuarioTipo);
        agendamento.setStatus(novoStatus);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        return paraDTO(salvo, nomeDoPaciente(salvo.getIdPaciente()));
    }

    @Transactional
    public void cancelar(Integer id, Integer usuarioId, String usuarioTipo) {
        agendamentoRepository.delete(carregarComPosse(id, usuarioId, usuarioTipo));
    }

    private Agendamento carregarComPosse(Integer id, Integer usuarioId, String usuarioTipo) {
        Agendamento agendamento = agendamentoRepository.findById(id)
                .orElseThrow(() -> new AuthException("Agendamento não encontrado.", HttpStatus.NOT_FOUND));
        exigirAcessoAoProfissional(agendamento.getIdFisioterapeuta(), usuarioId, usuarioTipo);
        return agendamento;
    }

    private Fisioterapeuta exigirAcessoAoProfissional(Integer idFisioterapeuta, Integer usuarioId, String usuarioTipo) {
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(idFisioterapeuta)
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.BAD_REQUEST));
        PosseChecker.exigirDonoOuClinicaDona(fisioterapeuta.getId(), fisioterapeuta.getIdClinica(),
                usuarioId, usuarioTipo);
        return fisioterapeuta;
    }

    /**
     * Remarcar é mover uma consulta que ainda vai acontecer. Consulta de um
     * dia que já passou, ou que já teve presença registrada, é histórico:
     * mudar a data dela reescreveria o passado e apagaria o registro do
     * atendimento (ou da falta). Para atender de novo, marca-se uma consulta
     * nova, e a antiga fica como está.
     */
    private void exigirQueAindaNaoTenhaAcontecido(Agendamento agendamento) {
        String status = agendamento.getStatus();
        if ("REALIZADA".equals(status) || "FALTOU".equals(status)) {
            throw new AuthException(
                    "Esta consulta já teve a presença registrada e não pode ser remarcada. "
                            + "Marque uma nova consulta para o paciente.",
                    HttpStatus.BAD_REQUEST);
        }
        if (agendamento.getDataAgendamento() != null
                && agendamento.getDataAgendamento().isBefore(LocalDate.now())) {
            throw new AuthException(
                    "Esta consulta já aconteceu e não pode ser remarcada. "
                            + "Marque uma nova consulta para o paciente.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Barra o "voltar no tempo": uma consulta só pode ser marcada para daqui
     * para frente. Sem isso dava para encher a agenda de datas passadas, que
     * nunca apareciam na lista de próximos e ainda assim ocupavam horário.
     */
    private void exigirDataFutura(LocalDate data, LocalTime hora) {
        if (LocalDateTime.of(data, hora).isBefore(LocalDateTime.now())) {
            throw new AuthException(
                    "Não é possível agendar em uma data ou horário que já passou. Escolha um horário futuro.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * A consulta inteira — começo e fim, pela duração padrão configurada —
     * precisa caber no horário de funcionamento das configurações.
     */
    private void exigirDentroDoExpediente(Integer idFisioterapeuta, LocalDate data, LocalTime hora) {
        ConfiguracaoDTO config = configuracaoService.janelaDeAtendimento(idFisioterapeuta);
        LocalTime abertura = config.getHoraAbertura();
        LocalTime fechamento = config.getHoraFechamento();
        if (abertura == null || fechamento == null) {
            return;
        }

        int duracao = config.getDuracaoPadraoMin();
        LocalTime fim = hora.plusMinutes(duracao);
        // fim.isBefore(hora) = a sessão atravessaria a meia-noite.
        boolean cabe = !hora.isBefore(abertura) && !fim.isAfter(fechamento) && !fim.isBefore(hora);

        if (!cabe) {
            throw new AuthException(
                    "Fora do horário de atendimento (" + abertura.format(HORA_BR) + " às "
                            + fechamento.format(HORA_BR) + "). Uma sessão de " + duracao
                            + " min precisa começar e terminar dentro desse intervalo.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Recusa dois atendimentos sobrepostos do mesmo profissional, usando a
     * duração padrão configurada como tamanho de cada sessão. Só age se o
     * aviso de conflito estiver ligado. {@code idIgnorar}
     * deixa de fora a própria consulta que está sendo remarcada.
     */
    private void exigirHorarioLivre(Integer idFisioterapeuta, LocalDate data, LocalTime hora, Integer idIgnorar) {
        ConfiguracaoDTO config = configuracaoService.janelaDeAtendimento(idFisioterapeuta);
        if (!config.isAvisarConflito()) {
            return;
        }
        int duracao = config.getDuracaoPadraoMin();
        LocalTime fimNovo = hora.plusMinutes(duracao);

        boolean conflita = agendamentoRepository
                .findByIdFisioterapeutaAndDataAgendamento(idFisioterapeuta, data)
                .stream()
                .filter(existente -> idIgnorar == null || !idIgnorar.equals(existente.getId()))
                .anyMatch(existente -> {
                    LocalTime inicioExistente = existente.getHoraAgendamento();
                    LocalTime fimExistente = inicioExistente.plusMinutes(duracao);
                    return hora.isBefore(fimExistente) && inicioExistente.isBefore(fimNovo);
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
        return paraDTO(a, nomePaciente, null);
    }

    private AgendamentoDTO paraDTO(Agendamento a, String nomePaciente, String nomeFisioterapeuta) {
        AgendamentoDTO dto = new AgendamentoDTO(a.getId(), a.getDataAgendamento(), a.getHoraAgendamento(),
                a.getObservacao(), a.getIdPaciente(), nomePaciente, nomeFisioterapeuta);
        // Agendamentos criados antes do campo existir não têm status gravado.
        dto.setStatus(a.getStatus() == null ? "AGENDADA" : a.getStatus());
        dto.setIdFisioterapeuta(a.getIdFisioterapeuta());
        dto.setDataOriginal(a.getDataOriginal());
        dto.setHoraOriginal(a.getHoraOriginal());
        return dto;
    }

    private AgendamentoDTO sessaoParaDTO(Integer id, LocalDate data, LocalTime hora, Integer duracao,
                                          Integer idPaciente, Integer idFisioterapeuta, String nomeFisioterapeuta) {
        AgendamentoDTO dto = new AgendamentoDTO(id, data, hora, duracao != null ? duracao + " min" : null,
                idPaciente, nomeDoPaciente(idPaciente), nomeFisioterapeuta);
        dto.setStatus("REALIZADA");
        dto.setIdFisioterapeuta(idFisioterapeuta);
        return dto;
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
