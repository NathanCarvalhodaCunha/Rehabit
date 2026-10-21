package com.rehabit.demo;

import com.rehabit.config.RelogioConfig;
import com.rehabit.model.Agendamento;
import com.rehabit.model.Clinica;
import com.rehabit.model.Configuracao;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Notificacao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.AgendamentoRepository;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.ConfiguracaoRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.NotificacaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import com.rehabit.service.ExclusaoDeContaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Monta uma clínica inteira a partir de um {@link Roteiro}: a equipe, os
 * pacientes com anamnese e metas, meses de sessões com a evolução da
 * amplitude e da dor (cada uma com a consulta em que o paciente compareceu),
 * faltas, uma consulta remarcada, a agenda das próximas semanas e o sino da
 * clínica. É o que a conta de demonstração e a conta da apresentação têm em
 * comum — muda só quem é a clínica, quem atende e quem é atendido.
 *
 * Todas as datas são relativas ao dia em que roda: o histórico termina ontem
 * e a agenda começa amanhã. A conta de demonstração, que ninguém altera, se
 * refaz quando essas datas ficam para trás (refazerSeVencida).
 *
 * Nasce aqui, e não pelas rotas da API, porque a API só registra uma sessão
 * no momento em que ela acontece, e uma clínica de exemplo precisa de histórico.
 */
@Component
public class GeradorDeClinica {

    private static final Logger log = LoggerFactory.getLogger(GeradorDeClinica.class);

    /** Expediente da clínica: todo horário da agenda cabe nele. */
    private static final LocalTime ABERTURA = LocalTime.of(7, 0);
    private static final LocalTime FECHAMENTO = LocalTime.of(19, 0);
    private static final int DURACAO_PADRAO = 50;

    /**
     * "foto" é o caminho de uma imagem dentro do jar (ex.:
     * "apresentacao/clinica.png"), ou null para ficar sem foto. Quem a põe
     * na conta é a {@link ManutencaoDaClinica}, fora da transação da montagem.
     */
    public record DadosDaClinica(String nome, String email, String cnpj, String telefone, String endereco,
                                 String subtitulo, String descricao, String localidade, String foto) {
        public DadosDaClinica(String nome, String email, String cnpj, String telefone, String endereco,
                              String subtitulo, String descricao, String localidade) {
            this(nome, email, cnpj, telefone, endereco, subtitulo, descricao, localidade, null);
        }
    }

    /** "foto" como em {@link DadosDaClinica}. */
    public record Profissional(String nome, String email, String coffito, String especialidade,
                               String telefone, String descricao, String foto) {
        public Profissional(String nome, String email, String coffito, String especialidade,
                            String telefone, String descricao) {
            this(nome, email, coffito, especialidade, telefone, descricao, null);
        }
    }

    /**
     * Um paciente e o desenho do seu tratamento. "profissional" é a posição
     * de quem atende na lista de profissionais do roteiro. A amplitude vai de
     * ampInicial a ampFinal ao longo das sessões (ganho rápido no começo,
     * platô no fim), e a dor cai de dorInicial a dorFinal. "semanasParado"
     * afasta a última sessão de hoje — é o paciente de alta ou que parou.
     */
    public record PacienteFicticio(int profissional, String nome, String cpf, String sexo, LocalDate nascimento,
                                   String telefone, String email, String situacao, String queixa,
                                   String historico, String medicamentos, String contraindicacoes,
                                   String status, Set<DayOfWeek> dias, LocalTime horario, int sessoes,
                                   int semanasParado, double ampInicial, double ampFinal, double meta,
                                   int dorInicial, int dorFinal, int duracao) {
    }

    /**
     * A clínica inteira. A segunda consulta do primeiro paciente sai
     * remarcada, então ele deve estar em tratamento. "semanasDeAgenda" é até
     * onde a agenda vai a partir de amanhã.
     */
    public record Roteiro(DadosDaClinica clinica, List<Profissional> profissionais,
                          List<PacienteFicticio> pacientes, int semanasDeAgenda) {
    }

    /** Prontuário por fase do tratamento: começo, meio e reta final. */
    private static final List<List<String>> PRONTUARIO = List.of(
            List.of("Mobilização passiva e liberação miofascial. Paciente relata menos rigidez ao acordar.",
                    "Exercícios ativo-assistidos. Boa tolerância, sem edema após a sessão.",
                    "Crioterapia ao final. Dor mais localizada que na semana anterior."),
            List.of("Fortalecimento isométrico e alongamento. Ganho de amplitude perceptível.",
                    "Exercícios ativos com faixa elástica leve. Paciente mais confiante no movimento.",
                    "Progressão de carga no fortalecimento. Dor apenas no fim do arco de movimento.",
                    "Treino proprioceptivo e de controle motor. Execução mais estável.",
                    "Paciente relata piora após esforço no trabalho; sessão adaptada com menos carga."),
            List.of("Treino funcional das atividades do dia a dia. Sem dor durante os exercícios.",
                    "Reavaliação com o goniômetro: evolução dentro do esperado para a meta.",
                    "Programa domiciliar revisado; paciente executa os exercícios sem ajuda."));

    /** O que ainda está por vir na agenda; FALTOU é passado por definição. */
    static final List<String> STATUS_A_VIR = List.of("AGENDADA", "REMARCADA");

    static final List<String> OBSERVACOES_AGENDA = List.of(
            "Sessão de rotina", "Reavaliação com o goniômetro", "Fortalecimento",
            "Trazer os exames de imagem", "Sessão de rotina", "Treino funcional");

    private final ClinicaRepository clinicas;
    private final FisioterapeutaRepository fisioterapeutas;
    private final PacienteRepository pacientes;
    private final SessaoRepository sessoes;
    private final MedicaoRepository medicoes;
    private final AgendamentoRepository agendamentos;
    private final ConfiguracaoRepository configuracoes;
    private final NotificacaoRepository notificacoes;
    private final PasswordEncoder passwordEncoder;
    private final ExclusaoDeContaService exclusao;
    private final Clock relogio;

    public GeradorDeClinica(ClinicaRepository clinicas, FisioterapeutaRepository fisioterapeutas,
                            PacienteRepository pacientes, SessaoRepository sessoes,
                            MedicaoRepository medicoes, AgendamentoRepository agendamentos,
                            ConfiguracaoRepository configuracoes, NotificacaoRepository notificacoes,
                            PasswordEncoder passwordEncoder, ExclusaoDeContaService exclusao, Clock relogio) {
        this.clinicas = clinicas;
        this.fisioterapeutas = fisioterapeutas;
        this.pacientes = pacientes;
        this.sessoes = sessoes;
        this.medicoes = medicoes;
        this.agendamentos = agendamentos;
        this.configuracoes = configuracoes;
        this.notificacoes = notificacoes;
        this.passwordEncoder = passwordEncoder;
        this.exclusao = exclusao;
        this.relogio = relogio;
    }

    /**
     * Para a conta de demonstração: as datas dela são as do dia em que foi
     * montada, e ninguém mexe nela (é só de leitura). Com os dias, o
     * histórico para de chegar ao presente e a agenda vira passado — consulta
     * "marcada" para a semana anterior. Quando a primeira consulta por vir já
     * é de hoje ou de antes (ou não sobrou nenhuma), a clínica inteira é
     * apagada e montada de novo com as datas de hoje.
     *
     * Num fim de semana, a agenda montada na sexta só começa na segunda: a
     * conta segue valendo até lá. Nunca usar numa conta que aceita
     * alterações — o que mudassem nela iria junto.
     *
     * @return true se criou ou refez; false se ainda estava em dia.
     */
    @Transactional
    public boolean refazerSeVencida(Roteiro roteiro, String senha) {
        Optional<Clinica> existente = clinicas.findByEmail(roteiro.clinica().email());
        if (existente.isPresent()) {
            LocalDate hoje = LocalDate.now(relogio.withZone(RelogioConfig.FUSO));
            Optional<LocalDate> primeiraAVir = primeiraConsultaAVir(existente.get());
            if (primeiraAVir.isPresent() && primeiraAVir.get().isAfter(hoje)) {
                return false;
            }
            exclusao.apagarClinicaInteira(existente.get());
            // A exclusão da linha da clínica fica na fila até o flush; sem ele,
            // a clínica nova (mesmo e-mail e CNPJ, colunas únicas) entraria antes.
            clinicas.flush();
            log.info("{}: a primeira consulta por vir era {}; refazendo com as datas de hoje.",
                    roteiro.clinica().nome(), primeiraAVir.map(LocalDate::toString).orElse("nenhuma"));
            // Só apaga se conseguir montar de novo: se outra clínica ficou com o
            // CNPJ, a exceção desfaz a exclusão e a conta antiga continua.
            if (!criarSeNaoExistir(roteiro, senha)) {
                throw new IllegalStateException(roteiro.clinica().nome() + " não pôde ser montada de novo.");
            }
            return true;
        }
        return criarSeNaoExistir(roteiro, senha);
    }

    private Optional<LocalDate> primeiraConsultaAVir(Clinica clinica) {
        List<Integer> idsPacientes = pacientes.findByIdClinica(clinica.getId()).stream()
                .map(Paciente::getId)
                .toList();
        if (idsPacientes.isEmpty()) return Optional.empty();
        return agendamentos.findFirstByIdPacienteInAndStatusInOrderByDataAgendamentoAsc(idsPacientes, STATUS_A_VIR)
                .map(Agendamento::getDataAgendamento);
    }

    /**
     * Cria a clínica inteira numa transação só: se algo falha no meio, nada
     * fica pela metade — e a próxima subida tenta de novo. Todas as contas do
     * roteiro (clínica e profissionais) entram com a mesma senha.
     *
     * @return true se criou; false se a clínica já existia.
     */
    @Transactional
    public boolean criarSeNaoExistir(Roteiro roteiro, String senha) {
        DadosDaClinica dados = roteiro.clinica();
        // Pelo CNPJ também: numa conta que aceita alterações a clínica pode ter
        // trocado o e-mail, e criar de novo esbarraria no CNPJ (coluna única).
        if (clinicas.existsByEmail(dados.email()) || clinicas.existsByCnpj(dados.cnpj())) {
            log.info("{} já existe (e-mail {} ou CNPJ {} já cadastrado); nada a fazer.",
                    dados.nome(), dados.email(), dados.cnpj());
            return false;
        }
        LocalDateTime agora = LocalDateTime.now(relogio.withZone(RelogioConfig.FUSO)).withNano(0);
        String senhaCifrada = passwordEncoder.encode(senha);

        Clinica clinica = criarClinica(dados, senhaCifrada);
        List<Fisioterapeuta> equipe = new ArrayList<>();
        for (Profissional p : roteiro.profissionais()) {
            equipe.add(criarProfissional(p, clinica, dados.localidade(), senhaCifrada));
        }

        List<Sessao> recentes = new ArrayList<>();
        List<PacienteFicticio> roteiroPacientes = roteiro.pacientes();
        for (int i = 0; i < roteiroPacientes.size(); i++) {
            PacienteFicticio ficticio = roteiroPacientes.get(i);
            Fisioterapeuta responsavel = equipe.get(ficticio.profissional());
            // Um em cada quatro pacientes em tratamento faltou a uma consulta
            // há umas três semanas: aquele dia fica sem sessão, com a falta na agenda.
            LocalDate falta = "Ativo".equals(ficticio.status()) && i % 4 == 1
                    ? diaDeAtendimentoAte(ficticio, agora.toLocalDate().minusWeeks(3))
                    : null;
            List<LocalDate> datas = datasDasSessoes(ficticio, agora.toLocalDate(), falta);
            Paciente paciente = criarPaciente(ficticio, responsavel, datas.get(0));
            recentes.addAll(criarSessoes(ficticio, paciente, datas));
            criarAgenda(ficticio, paciente, i, agora.toLocalDate(), datas, falta, roteiro.semanasDeAgenda());
        }
        criarNotificacoes(clinica, recentes);

        log.info("{} criada: {} ({} profissionais, {} pacientes).", dados.nome(), dados.email(),
                roteiro.profissionais().size(), roteiroPacientes.size());
        return true;
    }

    private Clinica criarClinica(DadosDaClinica dados, String senhaCifrada) {
        Clinica c = new Clinica();
        c.setNome(dados.nome());
        c.setEmail(dados.email());
        c.setSenha(senhaCifrada);
        c.setCnpj(dados.cnpj());
        c.setTelefone(dados.telefone());
        c.setEndereco(dados.endereco());
        c.setSubtitulo(dados.subtitulo());
        c.setDescricao(dados.descricao());
        c.setTutorialVisto(true);
        Clinica salva = clinicas.save(c);

        Configuracao horario = new Configuracao();
        horario.setTipoUsuario("CLINICA");
        horario.setIdUsuario(salva.getId());
        horario.setHoraAbertura(ABERTURA);
        horario.setHoraFechamento(FECHAMENTO);
        horario.setDuracaoPadraoMin(DURACAO_PADRAO);
        horario.setAvisarConflito(true);
        configuracoes.save(horario);
        return salva;
    }

    private Fisioterapeuta criarProfissional(Profissional p, Clinica clinica, String localidade,
                                             String senhaCifrada) {
        Fisioterapeuta f = new Fisioterapeuta();
        f.setNome(p.nome());
        f.setEmail(p.email());
        f.setSenha(senhaCifrada);
        f.setCoffito(p.coffito());
        f.setEspecialidade(p.especialidade());
        f.setTelefone(p.telefone());
        f.setDescricao(p.descricao());
        f.setLocalidade(localidade);
        f.setIdClinica(clinica.getId());
        f.setTutorialVisto(true);
        return fisioterapeutas.save(f);
    }

    private Paciente criarPaciente(PacienteFicticio ficticio, Fisioterapeuta responsavel, LocalDate primeiraSessao) {
        Paciente p = new Paciente();
        p.setNome(ficticio.nome());
        p.setCpf(ficticio.cpf());
        p.setTelefone(ficticio.telefone());
        p.setEmail(ficticio.email());
        p.setSexo(ficticio.sexo());
        p.setDataNascimento(ficticio.nascimento());
        // A avaliação que abriu o tratamento foi a primeira sessão.
        p.setDataInicioTratamento(primeiraSessao);
        p.setSituacao(ficticio.situacao());
        p.setStatus(ficticio.status());
        p.setQueixaPrincipal(ficticio.queixa());
        p.setHistoricoClinico(ficticio.historico());
        p.setMedicamentos(ficticio.medicamentos());
        p.setContraindicacoes(ficticio.contraindicacoes());
        p.setMetaAmplitude(BigDecimal.valueOf(ficticio.meta()).setScale(2, RoundingMode.HALF_UP));
        p.setMetaData(primeiraSessao.plusWeeks(ficticio.sessoes() / ficticio.dias().size() + 4L));
        p.setIdClinica(responsavel.getIdClinica());
        p.setIdFisioterapeuta(responsavel.getId());
        return pacientes.save(p);
    }

    /**
     * Os dias de atendimento, do mais antigo ao mais recente: voltando a
     * partir de ontem (ou de algumas semanas atrás, para quem parou), pega os
     * dias da semana em que o paciente é atendido até completar as sessões.
     * O dia da falta, quando há, fica de fora.
     */
    private static List<LocalDate> datasDasSessoes(PacienteFicticio ficticio, LocalDate hoje, LocalDate falta) {
        List<LocalDate> datas = new ArrayList<>();
        LocalDate dia = hoje.minusDays(1).minusWeeks(ficticio.semanasParado());
        while (datas.size() < ficticio.sessoes()) {
            if (ficticio.dias().contains(dia.getDayOfWeek()) && !dia.equals(falta)) datas.add(dia);
            dia = dia.minusDays(1);
        }
        Collections.reverse(datas);
        return datas;
    }

    /** O dia de atendimento do paciente mais próximo de "limite", sem passar dele. */
    private static LocalDate diaDeAtendimentoAte(PacienteFicticio ficticio, LocalDate limite) {
        LocalDate dia = limite;
        while (!ficticio.dias().contains(dia.getDayOfWeek())) dia = dia.minusDays(1);
        return dia;
    }

    private List<Sessao> criarSessoes(PacienteFicticio ficticio, Paciente paciente, List<LocalDate> datas) {
        List<Sessao> criadas = new ArrayList<>();
        int n = datas.size();
        for (int k = 0; k < n; k++) {
            double t = n == 1 ? 1 : (double) k / (n - 1);
            // Ganho rápido no começo e platô no fim, com a oscilação normal de
            // um dia para o outro — nenhuma evolução real é uma reta.
            double progresso = 1 - Math.pow(1 - t, 1.6);
            double amplitude = ficticio.ampInicial() + (ficticio.ampFinal() - ficticio.ampInicial()) * progresso
                    + (k == 0 || k == n - 1 ? 0 : Math.sin(k * 1.7) * 1.8);
            int dor = (int) Math.round(ficticio.dorInicial() + (ficticio.dorFinal() - ficticio.dorInicial()) * t
                    + (k % 5 == 3 ? 1 : 0));
            int duracao = ficticio.duracao() + (k % 3 == 1 ? 5 : k % 3 == 2 ? -5 : 0);
            // A sessão é registrada ao terminar: horário marcado + duração,
            // com a variação de alguns minutos de um dia para o outro.
            LocalTime registro = ficticio.horario().plusMinutes(duracao - 6 + (k * 7) % 9);

            Sessao s = new Sessao();
            s.setIdPaciente(paciente.getId());
            s.setIdFisioterapeuta(paciente.getIdFisioterapeuta());
            s.setDataSessao(datas.get(k));
            s.setHoraSessao(registro);
            s.setDuracao(duracao);
            s.setDor(Math.max(0, Math.min(10, dor)));
            s.setObservacoes(prontuario(k, t));
            Sessao salva = sessoes.save(s);

            Medicao m = new Medicao();
            m.setIdSessao(salva.getId());
            m.setAmplitudeMedia(BigDecimal.valueOf(amplitude).setScale(1, RoundingMode.HALF_UP));
            m.setDataMedicao(datas.get(k));
            m.setHoraMedicao(registro);
            medicoes.save(m);
            criadas.add(salva);
        }
        return criadas;
    }

    static String prontuario(int k, double t) {
        if (k == 0) {
            return "Avaliação inicial. Amplitude limitada pela dor; orientados exercícios domiciliares.";
        }
        // Uma sessão em cada quatro fica sem anotação, como na vida real.
        if (k % 4 == 2) return null;
        List<String> fase = PRONTUARIO.get(t < 0.35 ? 0 : t < 0.75 ? 1 : 2);
        return fase.get(k % fase.size());
    }

    /**
     * Agenda do paciente. Cada sessão do histórico saiu de uma consulta no
     * horário de sempre, com a presença marcada — é o que a aba "Realizadas"
     * da agenda do profissional lista. Quem está em tratamento tem ainda as
     * próximas semanas nos dias e horários de sempre, a falta de quem faltou
     * e uma consulta remarcada, para a tela mostrar todos os estados. Cada
     * paciente de um profissional tem horário próprio, então nada colide.
     */
    private void criarAgenda(PacienteFicticio ficticio, Paciente paciente, int indice, LocalDate hoje,
                             List<LocalDate> atendimentos, LocalDate falta, int semanas) {
        for (int k = 0; k < atendimentos.size(); k++) {
            Agendamento a = novoAgendamento(paciente, atendimentos.get(k), ficticio.horario(), k == 0
                    ? "Avaliação inicial"
                    : OBSERVACOES_AGENDA.get((indice + k) % OBSERVACOES_AGENDA.size()));
            a.setStatus("REALIZADA");
            agendamentos.save(a);
        }
        if (!"Ativo".equals(ficticio.status())) return;

        if (falta != null) {
            Agendamento a = novoAgendamento(paciente, falta, ficticio.horario(), "Não compareceu nem avisou");
            a.setStatus("FALTOU");
            agendamentos.save(a);
        }

        int n = 0;
        for (LocalDate dia = hoje.plusDays(1); !dia.isAfter(hoje.plusWeeks(semanas)); dia = dia.plusDays(1)) {
            if (!ficticio.dias().contains(dia.getDayOfWeek())) continue;
            Agendamento a = novoAgendamento(paciente, dia, ficticio.horario(),
                    OBSERVACOES_AGENDA.get((indice + n) % OBSERVACOES_AGENDA.size()));
            // A segunda consulta do primeiro paciente foi remarcada: o dia original fica guardado.
            if (indice == 0 && n == 1) {
                a.setDataOriginal(dia.minusDays(1));
                a.setHoraOriginal(ficticio.horario());
                a.setStatus("REMARCADA");
                a.setObservacao("Remarcada a pedido do paciente");
            }
            agendamentos.save(a);
            n++;
        }
    }

    static Agendamento novoAgendamento(Paciente paciente, LocalDate dia, LocalTime hora, String obs) {
        Agendamento a = new Agendamento();
        a.setIdPaciente(paciente.getId());
        a.setIdFisioterapeuta(paciente.getIdFisioterapeuta());
        a.setDataAgendamento(dia);
        a.setHoraAgendamento(hora);
        a.setObservacao(obs);
        a.setStatus("AGENDADA");
        return a;
    }

    /** O sino da clínica com as últimas sessões; só as três mais novas não lidas. */
    private void criarNotificacoes(Clinica clinica, List<Sessao> sessoesCriadas) {
        List<Sessao> maisNovas = new ArrayList<>(sessoesCriadas.stream()
                .sorted((a, b) -> LocalDateTime.of(b.getDataSessao(), b.getHoraSessao())
                        .compareTo(LocalDateTime.of(a.getDataSessao(), a.getHoraSessao())))
                .limit(8)
                .toList());
        for (int i = 0; i < maisNovas.size(); i++) {
            Sessao s = maisNovas.get(i);
            String nome = pacientes.findById(s.getIdPaciente()).map(Paciente::getNome).orElse("paciente");
            Notificacao n = new Notificacao();
            n.setIdClinica(clinica.getId());
            n.setTipo("NOVA_SESSAO");
            n.setMensagem("Nova sessão registrada para " + nome);
            n.setLida(i >= 3);
            n.setCriadaEm(LocalDateTime.of(s.getDataSessao(), s.getHoraSessao()));
            notificacoes.save(n);
        }
    }
}
