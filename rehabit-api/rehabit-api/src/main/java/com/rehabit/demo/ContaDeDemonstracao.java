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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.Set;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

/**
 * Conta de demonstração: uma clínica com cinco profissionais, dezesseis
 * pacientes e meses de atendimento, para apresentar e testar o Rehabit com
 * dados que se parecem com os de uma clínica de verdade.
 *
 * Liga com REHABIT_DEMO=true e roda na subida da API. É idempotente: se o
 * e-mail da clínica já existe, não faz nada — então pode ficar ligada.
 * Todas as datas são relativas ao dia em que roda: o histórico termina ontem
 * e a agenda começa amanhã.
 *
 * Nasce aqui, e não pelas rotas da API, porque a API só registra uma sessão
 * no momento em que ela acontece, e a demonstração precisa de histórico.
 *
 * Os e-mails usam o domínio .example, reservado para exemplos (RFC 2606):
 * uma recuperação de senha nunca vai parar na caixa de alguém de verdade.
 * Os telefones são fictícios.
 */
@Component
@ConditionalOnProperty(name = "rehabit.demo.ativar", havingValue = "true")
public class ContaDeDemonstracao implements ApplicationRunner {

    public static final String EMAIL_CLINICA = "contato@movimento.example";

    private static final Logger log = LoggerFactory.getLogger(ContaDeDemonstracao.class);

    /** Expediente da clínica: todo horário da agenda cabe nele. */
    private static final LocalTime ABERTURA = LocalTime.of(7, 0);
    private static final LocalTime FECHAMENTO = LocalTime.of(19, 0);
    private static final int DURACAO_PADRAO = 50;

    private record Profissional(String nome, String email, String coffito, String especialidade,
                                String telefone, String descricao) {
    }

    /**
     * Um paciente e o desenho do seu tratamento. A amplitude vai de
     * ampInicial a ampFinal ao longo das sessões (ganho rápido no começo,
     * platô no fim), e a dor cai de dorInicial a dorFinal. "semanasParado"
     * afasta a última sessão de hoje — é o paciente de alta ou que parou.
     */
    private record PacienteDemo(int profissional, String nome, String cpf, String sexo, LocalDate nascimento,
                                String telefone, String email, String situacao, String queixa,
                                String historico, String medicamentos, String contraindicacoes,
                                String status, Set<DayOfWeek> dias, LocalTime horario, int sessoes,
                                int semanasParado, double ampInicial, double ampFinal, double meta,
                                int dorInicial, int dorFinal, int duracao) {
    }

    private static final List<Profissional> PROFISSIONAIS = List.of(
            new Profissional("Ana Paula Ribeiro", "ana.ribeiro@movimento.example", "CREFITO-3/123456-F",
                    "Ortopedia e traumatologia", "(11) 98765-4321",
                    "Fisioterapeuta há 12 anos, com foco em reabilitação pós-operatória de joelho e ombro."),
            new Profissional("Rafael Moura", "rafael.moura@movimento.example", "CREFITO-3/234567-F",
                    "Fisioterapia esportiva", "(11) 97654-3210",
                    "Atende atletas amadores e profissionais em lesões de joelho, tornozelo e punho."),
            new Profissional("Juliana Castro", "juliana.castro@movimento.example", "CREFITO-3/345678-F",
                    "Fisioterapia neurofuncional", "(11) 96543-2109",
                    "Reabilitação de pacientes pós-AVC e com doenças neurológicas progressivas."),
            new Profissional("Carlos Eduardo Tanaka", "carlos.tanaka@movimento.example", "CREFITO-3/287104-F",
                    "Fisioterapia reumatológica", "(11) 95432-1098",
                    "Cuida de pacientes com artrite, espondilite e dor crônica, com ênfase em educação em dor."),
            new Profissional("Fernanda Oliveira Lima", "fernanda.lima@movimento.example", "CREFITO-3/301552-F",
                    "Fisioterapia gerontológica", "(11) 94321-0987",
                    "Especialista em idosos: prevenção de quedas e reabilitação após fraturas e próteses."));

    private static final List<PacienteDemo> PACIENTES = List.of(
            // --- Ana Paula Ribeiro, ortopedia ---
            new PacienteDemo(0, "Marcos Vinícius Almeida", "482.917.305-04", "Masculino", LocalDate.of(1988, 4, 12),
                    "(11) 94127-3308", "marcos.almeida@email.example", "Pós-operatório de LCA (joelho direito)",
                    "Dificuldade para dobrar o joelho direito e para subir escadas depois da cirurgia.",
                    "Reconstrução do ligamento cruzado anterior com enxerto do tendão patelar. Sem outras cirurgias.",
                    "Paracetamol 750 mg se houver dor.",
                    "Evitar agachamento profundo e cadeia cinética aberta com carga até a 12ª semana.",
                    "Ativo", Set.of(MONDAY, THURSDAY), LocalTime.of(8, 0), 16, 0, 62, 118, 130, 7, 2, 50),
            new PacienteDemo(0, "Helena Duarte Campos", "736.150.284-90", "Feminino", LocalDate.of(1956, 9, 3),
                    "(11) 99315-6072", "helena.campos@email.example", "Capsulite adesiva no ombro esquerdo",
                    "Não consegue levantar o braço esquerdo acima da cabeça; a dor piora à noite.",
                    "Diabetes tipo 2 controlada. Dor no ombro há quatro meses, sem trauma.",
                    "Metformina 850 mg; dipirona 1 g se houver dor.",
                    "Evitar mobilização vigorosa enquanto a dor noturna persistir.",
                    "Ativo", Set.of(TUESDAY, FRIDAY), LocalTime.of(9, 0), 14, 0, 78, 124, 160, 8, 4, 50),
            new PacienteDemo(0, "Luíza Fernandes", "915.372.648-00", "Feminino", LocalDate.of(1979, 11, 25),
                    "(11) 98460-1195", "luiza.fernandes@email.example", "Epicondilite lateral (cotovelo direito)",
                    "Dor ao segurar objetos, torcer panos e digitar.",
                    "Analista administrativa, oito horas por dia ao computador.",
                    "Ibuprofeno 400 mg nas crises, por no máximo três dias.",
                    "Nenhuma relatada.",
                    "Ativo", Set.of(WEDNESDAY), LocalTime.of(14, 0), 8, 0, 45, 68, 70, 6, 2, 45),
            new PacienteDemo(0, "Roberto Carlos Nunes", "264.839.150-98", "Masculino", LocalDate.of(1962, 6, 30),
                    "(11) 97218-4450", "roberto.nunes@email.example", "Artrose de quadril — pré-operatório",
                    "Dor ao caminhar mais de 15 minutos e ao calçar os sapatos.",
                    "Artroplastia total do quadril direito prevista para dezembro. Hipertenso.",
                    "Losartana 50 mg; paracetamol 750 mg.",
                    "Evitar flexão do quadril acima de 90° com rotação interna.",
                    "Ativo", Set.of(MONDAY, THURSDAY), LocalTime.of(10, 0), 10, 0, 72, 86, 100, 7, 5, 45),
            new PacienteDemo(0, "Camila Rocha", "507.184.923-23", "Feminino", LocalDate.of(1995, 8, 8),
                    "(11) 99871-2036", "camila.rocha@email.example", "Tendinite do supraespinhal",
                    "Dor no ombro direito ao nadar crawl.",
                    "Nadadora amadora, treina quatro vezes por semana.",
                    "Nenhum.", "Nenhuma.",
                    "Alta", Set.of(TUESDAY), LocalTime.of(16, 0), 10, 3, 128, 172, 170, 5, 0, 45),

            // --- Rafael Moura, esportiva ---
            new PacienteDemo(1, "Gustavo Lima Pereira", "839.261.574-37", "Masculino", LocalDate.of(1990, 3, 14),
                    "(11) 94552-8817", "gustavo.pereira@email.example", "Lesão de menisco medial (joelho esquerdo)",
                    "Travamento e dor no joelho esquerdo ao correr.",
                    "Corredor de rua, 40 km por semana. Lesão em treino de tiro.",
                    "Nenhum.", "Evitar rotação com o pé fixo e impacto até a liberação.",
                    "Ativo", Set.of(MONDAY, WEDNESDAY), LocalTime.of(7, 0), 12, 0, 95, 128, 135, 5, 1, 50),
            new PacienteDemo(1, "Beatriz Monteiro", "173.548.296-09", "Feminino", LocalDate.of(1983, 12, 2),
                    "(11) 98033-4721", "beatriz.monteiro@email.example", "Pós-operatório de fratura do punho",
                    "Punho direito rígido e fraco depois de retirar a tala.",
                    "Fratura do rádio distal em queda de bicicleta; placa e parafusos.",
                    "Cálcio e vitamina D.", "Sem carga axial no punho até a 10ª semana.",
                    "Ativo", Set.of(TUESDAY, THURSDAY), LocalTime.of(17, 0), 11, 0, 32, 61, 70, 6, 2, 45),
            // Sem telefone de propósito: nem todo paciente informa um, e é o
            // caso em que o lembrete no WhatsApp explica o que falta.
            new PacienteDemo(1, "Thiago Nascimento", "628.405.937-00", "Masculino", LocalDate.of(1998, 7, 21),
                    null, "thiago.nascimento@email.example", "Entorse de tornozelo grau II",
                    "Dor e insegurança no tornozelo esquerdo ao mudar de direção.",
                    "Jogador de futsal amador. Segunda entorse no mesmo tornozelo em um ano.",
                    "Nenhum.", "Nenhuma.",
                    "Ativo", Set.of(FRIDAY), LocalTime.of(18, 0), 7, 0, 8, 19, 20, 5, 1, 45),
            new PacienteDemo(1, "Larissa Teixeira Gomes", "390.716.842-96", "Feminino", LocalDate.of(2002, 1, 30),
                    "(11) 99704-6653", "larissa.gomes@email.example", "Luxação recidivante de patela",
                    "Sensação de o joelho direito sair do lugar ao descer escadas.",
                    "Bailarina. Dois episódios de luxação da patela direita no último ano.",
                    "Nenhum.", "Evitar valgo dinâmico e saltos até completar o fortalecimento.",
                    "Inativo", Set.of(WEDNESDAY), LocalTime.of(8, 0), 6, 5, 110, 126, 140, 4, 2, 45),

            // --- Juliana Castro, neurofuncional ---
            new PacienteDemo(2, "Antônio José Barbosa", "451.829.063-15", "Masculino", LocalDate.of(1949, 5, 19),
                    "(11) 99402-3158", null, "Rigidez de ombro pós-AVC",
                    "Pouca mobilidade no braço esquerdo; dificuldade para se vestir sozinho.",
                    "AVC isquêmico há quatro meses, hemiparesia à esquerda. Hipertenso e diabético.",
                    "AAS 100 mg; atorvastatina 20 mg; losartana 50 mg.",
                    "Cuidado com subluxação do ombro: não tracionar o membro afetado.",
                    "Ativo", Set.of(TUESDAY, FRIDAY), LocalTime.of(14, 0), 13, 0, 58, 84, 120, 4, 3, 60),
            new PacienteDemo(2, "Sônia Regina Prado", "814.637.205-80", "Feminino", LocalDate.of(1958, 10, 11),
                    "(11) 99128-5540", "sonia.prado@email.example", "Parkinson — rigidez e marcha",
                    "Passos curtos, dificuldade para levantar o braço e para virar na cama.",
                    "Diagnóstico de doença de Parkinson há seis anos.",
                    "Levodopa + benserazida 200/50 mg, três vezes ao dia.",
                    "Atender no período \"on\" da medicação; atenção ao risco de queda.",
                    "Ativo", Set.of(MONDAY, THURSDAY), LocalTime.of(15, 0), 12, 0, 118, 136, 150, 2, 1, 50),
            new PacienteDemo(2, "Eduardo Martins Leal", "297.053.418-50", "Masculino", LocalDate.of(1976, 2, 8),
                    "(11) 98247-3319", "eduardo.leal@email.example", "Paralisia do nervo radial (punho caído)",
                    "Não consegue levantar o punho direito.",
                    "Compressão do nervo radial depois de dormir com o braço apoiado, há sete semanas.",
                    "Complexo B.", "Usar a órtese de repouso entre as sessões.",
                    "Ativo", Set.of(WEDNESDAY), LocalTime.of(11, 0), 7, 0, 5, 38, 60, 1, 0, 45),

            // --- Carlos Eduardo Tanaka, reumatologia ---
            new PacienteDemo(3, "Maria Aparecida Souza", "685.190.374-10", "Feminino", LocalDate.of(1967, 4, 2),
                    "(11) 97611-0284", "maria.souza@email.example", "Artrite reumatoide — mãos e punhos",
                    "Rigidez nas mãos por mais de uma hora ao acordar.",
                    "Artrite reumatoide há nove anos, acompanhada por reumatologista.",
                    "Metotrexato 15 mg por semana; ácido fólico.",
                    "Evitar exercício resistido em articulação com sinovite ativa.",
                    "Ativo", Set.of(TUESDAY, THURSDAY), LocalTime.of(10, 0), 12, 0, 40, 58, 65, 6, 3, 50),
            new PacienteDemo(3, "José Ricardo Farias", "342.876.519-28", "Masculino", LocalDate.of(1971, 9, 17),
                    "(11) 99463-7702", "jose.farias@email.example", "Espondilite anquilosante",
                    "Dor lombar que melhora com movimento e piora em repouso.",
                    "HLA-B27 positivo. Diagnóstico há três anos.",
                    "Adalimumabe 40 mg a cada 15 dias.", "Evitar flexão forçada da coluna.",
                    "Ativo", Set.of(MONDAY), LocalTime.of(17, 0), 9, 0, 88, 104, 110, 5, 3, 50),

            // --- Fernanda Oliveira Lima, gerontologia ---
            new PacienteDemo(4, "Terezinha de Jesus Almeida", "956.314.827-46", "Feminino", LocalDate.of(1941, 12, 24),
                    "(11) 97736-5014", null, "Pós-operatório de prótese de joelho",
                    "Dificuldade para levantar da cadeira e caminhar sem o andador.",
                    "Artroplastia total do joelho esquerdo há oito semanas. Osteoporose.",
                    "Alendronato 70 mg por semana; cálcio e vitamina D.",
                    "Risco de queda: exercícios sempre com apoio e sem impacto.",
                    "Ativo", Set.of(MONDAY, THURSDAY), LocalTime.of(11, 0), 14, 0, 70, 102, 110, 6, 3, 50),
            new PacienteDemo(4, "Walter Hideki Sato", "128.590.643-89", "Masculino", LocalDate.of(1945, 3, 6),
                    "(11) 98815-2267", "walter.sato@email.example", "Fratura de úmero proximal",
                    "Ombro direito rígido depois de uma queda em casa.",
                    "Fratura do úmero proximal tratada com tipoia por quatro semanas. Mora sozinho.",
                    "Paracetamol 750 mg se houver dor.",
                    "Sem carga no membro superior direito até a consolidação completa.",
                    "Ativo", Set.of(TUESDAY, FRIDAY), LocalTime.of(8, 0), 10, 0, 60, 98, 130, 5, 3, 45));

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

    private static final List<String> OBSERVACOES_AGENDA = List.of(
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
    private final Clock relogio;
    private final String senha;

    public ContaDeDemonstracao(ClinicaRepository clinicas, FisioterapeutaRepository fisioterapeutas,
                               PacienteRepository pacientes, SessaoRepository sessoes,
                               MedicaoRepository medicoes, AgendamentoRepository agendamentos,
                               ConfiguracaoRepository configuracoes, NotificacaoRepository notificacoes,
                               PasswordEncoder passwordEncoder, Clock relogio,
                               @Value("${rehabit.demo.senha}") String senha) {
        this.clinicas = clinicas;
        this.fisioterapeutas = fisioterapeutas;
        this.pacientes = pacientes;
        this.sessoes = sessoes;
        this.medicoes = medicoes;
        this.agendamentos = agendamentos;
        this.configuracoes = configuracoes;
        this.notificacoes = notificacoes;
        this.passwordEncoder = passwordEncoder;
        this.relogio = relogio;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        criarSeNaoExistir();
    }

    /**
     * Cria a conta inteira numa transação só: se algo falha no meio, nada
     * fica pela metade — e a próxima subida tenta de novo.
     *
     * @return true se criou; false se a conta já existia.
     */
    @Transactional
    public boolean criarSeNaoExistir() {
        if (clinicas.existsByEmail(EMAIL_CLINICA)) {
            log.info("Conta de demonstração já existe ({}); nada a fazer.", EMAIL_CLINICA);
            return false;
        }
        LocalDateTime agora = LocalDateTime.now(relogio.withZone(RelogioConfig.FUSO)).withNano(0);
        String senhaCifrada = passwordEncoder.encode(senha);

        Clinica clinica = criarClinica(senhaCifrada);
        List<Fisioterapeuta> equipe = new ArrayList<>();
        for (Profissional p : PROFISSIONAIS) {
            equipe.add(criarProfissional(p, clinica, senhaCifrada));
        }

        List<Sessao> recentes = new ArrayList<>();
        for (int i = 0; i < PACIENTES.size(); i++) {
            PacienteDemo demo = PACIENTES.get(i);
            Fisioterapeuta responsavel = equipe.get(demo.profissional());
            // Um em cada quatro pacientes em tratamento faltou a uma consulta
            // há umas três semanas: aquele dia fica sem sessão, com a falta na agenda.
            LocalDate falta = "Ativo".equals(demo.status()) && i % 4 == 1
                    ? diaDeAtendimentoAte(demo, agora.toLocalDate().minusWeeks(3))
                    : null;
            List<LocalDate> datas = datasDasSessoes(demo, agora.toLocalDate(), falta);
            Paciente paciente = criarPaciente(demo, responsavel, datas.get(0));
            recentes.addAll(criarSessoes(demo, paciente, datas));
            criarAgenda(demo, paciente, i, agora.toLocalDate(), falta);
        }
        criarNotificacoes(clinica, recentes);

        log.info("Conta de demonstração criada: {} ({} profissionais, {} pacientes).",
                EMAIL_CLINICA, PROFISSIONAIS.size(), PACIENTES.size());
        return true;
    }

    private Clinica criarClinica(String senhaCifrada) {
        Clinica c = new Clinica();
        c.setNome("Clínica Movimento Fisioterapia");
        c.setEmail(EMAIL_CLINICA);
        c.setSenha(senhaCifrada);
        c.setCnpj("47.315.268/0001-86");
        c.setTelefone("(11) 3571-2040");
        c.setEndereco("Rua Domingos de Morais, 1450 — Vila Mariana, São Paulo — SP");
        c.setSubtitulo("Fisioterapia e reabilitação");
        c.setDescricao("Clínica de reabilitação ortopédica, esportiva, neurológica e geriátrica, com "
                + "acompanhamento da amplitude de movimento por goniômetro digital.");
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

    private Fisioterapeuta criarProfissional(Profissional p, Clinica clinica, String senhaCifrada) {
        Fisioterapeuta f = new Fisioterapeuta();
        f.setNome(p.nome());
        f.setEmail(p.email());
        f.setSenha(senhaCifrada);
        f.setCoffito(p.coffito());
        f.setEspecialidade(p.especialidade());
        f.setTelefone(p.telefone());
        f.setDescricao(p.descricao());
        f.setLocalidade("São Paulo — SP");
        f.setIdClinica(clinica.getId());
        f.setTutorialVisto(true);
        return fisioterapeutas.save(f);
    }

    private Paciente criarPaciente(PacienteDemo demo, Fisioterapeuta responsavel, LocalDate primeiraSessao) {
        Paciente p = new Paciente();
        p.setNome(demo.nome());
        p.setCpf(demo.cpf());
        p.setTelefone(demo.telefone());
        p.setEmail(demo.email());
        p.setSexo(demo.sexo());
        p.setDataNascimento(demo.nascimento());
        // A avaliação que abriu o tratamento foi a primeira sessão.
        p.setDataInicioTratamento(primeiraSessao);
        p.setSituacao(demo.situacao());
        p.setStatus(demo.status());
        p.setQueixaPrincipal(demo.queixa());
        p.setHistoricoClinico(demo.historico());
        p.setMedicamentos(demo.medicamentos());
        p.setContraindicacoes(demo.contraindicacoes());
        p.setMetaAmplitude(BigDecimal.valueOf(demo.meta()).setScale(2, RoundingMode.HALF_UP));
        p.setMetaData(primeiraSessao.plusWeeks(demo.sessoes() / demo.dias().size() + 4L));
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
    private static List<LocalDate> datasDasSessoes(PacienteDemo demo, LocalDate hoje, LocalDate falta) {
        List<LocalDate> datas = new ArrayList<>();
        LocalDate dia = hoje.minusDays(1).minusWeeks(demo.semanasParado());
        while (datas.size() < demo.sessoes()) {
            if (demo.dias().contains(dia.getDayOfWeek()) && !dia.equals(falta)) datas.add(dia);
            dia = dia.minusDays(1);
        }
        Collections.reverse(datas);
        return datas;
    }

    /** O dia de atendimento do paciente mais próximo de "limite", sem passar dele. */
    private static LocalDate diaDeAtendimentoAte(PacienteDemo demo, LocalDate limite) {
        LocalDate dia = limite;
        while (!demo.dias().contains(dia.getDayOfWeek())) dia = dia.minusDays(1);
        return dia;
    }

    private List<Sessao> criarSessoes(PacienteDemo demo, Paciente paciente, List<LocalDate> datas) {
        List<Sessao> criadas = new ArrayList<>();
        int n = datas.size();
        for (int k = 0; k < n; k++) {
            double t = n == 1 ? 1 : (double) k / (n - 1);
            // Ganho rápido no começo e platô no fim, com a oscilação normal de
            // um dia para o outro — nenhuma evolução real é uma reta.
            double progresso = 1 - Math.pow(1 - t, 1.6);
            double amplitude = demo.ampInicial() + (demo.ampFinal() - demo.ampInicial()) * progresso
                    + (k == 0 || k == n - 1 ? 0 : Math.sin(k * 1.7) * 1.8);
            int dor = (int) Math.round(demo.dorInicial() + (demo.dorFinal() - demo.dorInicial()) * t
                    + (k % 5 == 3 ? 1 : 0));
            int duracao = demo.duracao() + (k % 3 == 1 ? 5 : k % 3 == 2 ? -5 : 0);
            // A sessão é registrada ao terminar: horário marcado + duração,
            // com a variação de alguns minutos de um dia para o outro.
            LocalTime registro = demo.horario().plusMinutes(duracao - 6 + (k * 7) % 9);

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

    private static String prontuario(int k, double t) {
        if (k == 0) {
            return "Avaliação inicial. Amplitude limitada pela dor; orientados exercícios domiciliares.";
        }
        // Uma sessão em cada quatro fica sem anotação, como na vida real.
        if (k % 4 == 2) return null;
        List<String> fase = PRONTUARIO.get(t < 0.35 ? 0 : t < 0.75 ? 1 : 2);
        return fase.get(k % fase.size());
    }

    /**
     * Agenda de quem está em tratamento: as próximas duas semanas nos dias e
     * horários de sempre, a falta de quem faltou e uma consulta remarcada,
     * para a tela mostrar todos os estados. Cada paciente de um profissional
     * tem horário próprio, então nada colide.
     */
    private void criarAgenda(PacienteDemo demo, Paciente paciente, int indice, LocalDate hoje, LocalDate falta) {
        if (!"Ativo".equals(demo.status())) return;

        if (falta != null) {
            Agendamento a = novoAgendamento(paciente, falta, demo.horario(), "Não compareceu nem avisou");
            a.setStatus("FALTOU");
            agendamentos.save(a);
        }

        int n = 0;
        for (LocalDate dia = hoje.plusDays(1); !dia.isAfter(hoje.plusWeeks(2)); dia = dia.plusDays(1)) {
            if (!demo.dias().contains(dia.getDayOfWeek())) continue;
            Agendamento a = novoAgendamento(paciente, dia, demo.horario(),
                    OBSERVACOES_AGENDA.get((indice + n) % OBSERVACOES_AGENDA.size()));
            // Uma consulta do Marcos foi remarcada: o dia original fica guardado.
            if (indice == 0 && n == 1) {
                a.setDataOriginal(dia.minusDays(1));
                a.setHoraOriginal(demo.horario());
                a.setStatus("REMARCADA");
                a.setObservacao("Remarcada a pedido do paciente");
            }
            agendamentos.save(a);
            n++;
        }
    }

    private static Agendamento novoAgendamento(Paciente paciente, LocalDate dia, LocalTime hora, String obs) {
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
