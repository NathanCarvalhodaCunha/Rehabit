package com.rehabit.demo;

import com.rehabit.demo.GeradorDeClinica.DadosDaClinica;
import com.rehabit.demo.GeradorDeClinica.PacienteFicticio;
import com.rehabit.demo.GeradorDeClinica.Profissional;
import com.rehabit.demo.GeradorDeClinica.Roteiro;
import com.rehabit.security.PoliticaDeSenha;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

/**
 * Conta da apresentação do TCC: a Clínica Alvorada, de Atibaia, com quatro
 * fisioterapeutas e catorze pacientes com meses de atendimento. Ao contrário
 * da demonstração, é uma conta comum: dá para editar, cadastrar, excluir e
 * registrar sessão com o goniômetro, e nenhuma tela fala em demonstração.
 *
 * Liga com REHABIT_APRESENTACAO=true e roda na subida da API e toda
 * madrugada. Cria a conta se o e-mail da clínica ainda não existe (veja
 * {@link GeradorDeClinica}) e, existindo, nunca a apaga nem a refaz: quem a
 * mantém em dia é a {@link ManutencaoDaClinica}, que completa as fotos que
 * faltam e faz a clínica seguir atendendo — as consultas que passaram viram
 * sessões, e a agenda anda. Assim, no dia da banca o histórico chega até a
 * véspera sem recriar nada (nem parear o goniômetro de novo).
 *
 * A senha vem de REHABIT_APRESENTACAO_SENHA, sem valor padrão: o repositório
 * é público, e uma conta que aceita alterações não pode ter a senha nele.
 * Sem senha (ou com uma que a política recusaria), nada é criado. É a mesma
 * para a clínica e os profissionais, e a variável manda: se mudar, a próxima
 * subida troca a senha das cinco contas (e encerra as sessões abertas delas).
 *
 * As fotos dos profissionais são retratos ilustrados, não rostos de gente de
 * verdade: estilo Avataaars, de Pablo Stanley (livre para uso pessoal e
 * comercial), gerados com a biblioteca DiceBear. O logo da clínica foi
 * desenhado para o projeto. Ficam em src/main/resources/apresentacao.
 *
 * Os e-mails são apelidos do tccrehabit@gmail.com — o Gmail entrega na mesma
 * caixa tudo o que tem "+algo" antes do @ —, então a recuperação de senha
 * de qualquer uma das contas chega à equipe.
 *
 * Os pacientes são inventados: dado de saúde de gente de verdade não entra
 * num repositório público. Os CPFs têm dígitos verificadores válidos e não
 * repetem os da demonstração (a coluna é única). Os telefones também são
 * inventados — para mostrar o lembrete no WhatsApp, troque o de um paciente
 * pelo de alguém da equipe.
 *
 * A agenda cobre três semanas, e boa parte dos pacientes é atendida às
 * terças, o dia da apresentação (27/10/2026).
 */
@Component
@ConditionalOnProperty(name = "rehabit.apresentacao.ativar", havingValue = "true")
public class ContaDeApresentacao implements ApplicationRunner {

    public static final String EMAIL_CLINICA = "tccrehabit+clinica@gmail.com";

    private static final Logger log = LoggerFactory.getLogger(ContaDeApresentacao.class);

    private static final DadosDaClinica CLINICA = new DadosDaClinica(
            "Clínica Alvorada Fisioterapia", EMAIL_CLINICA, "20.239.361/0001-00", "(11) 4412-3870",
            "Rua José Alvim, 812 — Centro, Atibaia — SP", "Fisioterapia e reabilitação",
            "Clínica de fisioterapia em Atibaia, com atendimento ortopédico, esportivo, neurológico e "
                    + "para idosos, e acompanhamento da amplitude de movimento por goniômetro digital.",
            "Atibaia — SP", "apresentacao/clinica.png");

    private static final List<Profissional> PROFISSIONAIS = List.of(
            new Profissional("Mariana Costa Bertolini", "tccrehabit+mariana@gmail.com", "CREFITO-3/198374-F",
                    "Ortopedia e traumatologia", "(11) 98142-6630",
                    "Fisioterapeuta há nove anos, com foco em reabilitação pós-operatória de joelho, ombro e cotovelo.",
                    "apresentacao/mariana.png"),
            new Profissional("Lucas Henrique Prado", "tccrehabit+lucas@gmail.com", "CREFITO-3/256819-F",
                    "Fisioterapia esportiva", "(11) 97503-2194",
                    "Atende corredores, lutadores e jogadores de futebol amador em lesões de joelho, tornozelo e ombro.",
                    "apresentacao/lucas.png"),
            new Profissional("Patrícia Yamamoto Reis", "tccrehabit+patricia@gmail.com", "CREFITO-3/172045-F",
                    "Fisioterapia neurofuncional", "(11) 99627-4018",
                    "Reabilitação de pacientes pós-AVC, com Parkinson e com lesões de nervos periféricos.",
                    "apresentacao/patricia.png"),
            new Profissional("Renato Aguiar Fonseca", "tccrehabit+renato@gmail.com", "CREFITO-3/224908-F",
                    "Fisioterapia gerontológica", "(11) 98830-5172",
                    "Cuida de idosos: prevenção de quedas e reabilitação depois de fraturas e próteses.",
                    "apresentacao/renato.png"));

    private static final List<PacienteFicticio> PACIENTES = List.of(
            // --- Mariana Costa Bertolini, ortopedia ---
            new PacienteFicticio(0, "Rodrigo Tavares Mendes", "300.766.801-85", "Masculino", LocalDate.of(1985, 2, 17),
                    "(11) 97481-2259", "rodrigo.tmendes@gmail.com", "Pós-operatório de LCA (joelho esquerdo)",
                    "Joelho esquerdo rígido e inchado no fim do dia; dificuldade para descer escadas.",
                    "Reconstrução do ligamento cruzado anterior com enxerto de isquiotibiais, após entorse no futebol.",
                    "Dipirona 1 g se houver dor.",
                    "Sem exercício em cadeia cinética aberta com carga até a 12ª semana.",
                    "Ativo", Set.of(TUESDAY, FRIDAY), LocalTime.of(8, 0), 15, 0, 70, 121, 135, 7, 2, 50),
            new PacienteFicticio(0, "Cláudia Regina Fontes", "637.346.237-40", "Feminino", LocalDate.of(1961, 7, 9),
                    "(11) 99206-7713", "claudiafontes61@hotmail.com", "Capsulite adesiva no ombro direito",
                    "Não consegue pentear o cabelo nem alcançar a prateleira com o braço direito.",
                    "Hipotireoidismo em tratamento. Dor no ombro há cinco meses, sem queda nem trauma.",
                    "Levotiroxina 75 mcg; paracetamol 750 mg se houver dor.",
                    "Evitar mobilização vigorosa nos dias de dor noturna intensa.",
                    "Ativo", Set.of(MONDAY, WEDNESDAY), LocalTime.of(9, 0), 13, 0, 85, 128, 165, 8, 4, 50),
            new PacienteFicticio(0, "Felipe Augusto Rangel", "562.455.101-04", "Masculino", LocalDate.of(1993, 5, 28),
                    "(11) 98357-1046", "felipe.rangel93@gmail.com", "Fratura da cabeça do rádio (cotovelo direito)",
                    "Não consegue esticar nem dobrar o cotovelo direito por completo.",
                    "Queda de moto; fixação da cabeça do rádio com parafusos há sete semanas.",
                    "Nenhum.", "Sem carga no braço direito até a liberação do ortopedista.",
                    "Ativo", Set.of(TUESDAY, THURSDAY), LocalTime.of(10, 0), 10, 0, 85, 128, 140, 6, 2, 45),
            new PacienteFicticio(0, "Simone Aparecida Brito", "344.983.414-22", "Feminino", LocalDate.of(1974, 10, 3),
                    "(11) 99674-3381", null, "Síndrome do impacto no ombro esquerdo",
                    "Dor no ombro esquerdo ao estender roupa e ao pegar objetos no alto.",
                    "Costureira, trabalha com os braços elevados boa parte do dia.",
                    "Nenhum.", "Nenhuma.",
                    "Alta", Set.of(WEDNESDAY), LocalTime.of(16, 0), 10, 3, 110, 165, 160, 6, 0, 45),

            // --- Lucas Henrique Prado, esportiva ---
            new PacienteFicticio(1, "Bruno Cezar Oliveira", "286.595.916-31", "Masculino", LocalDate.of(1996, 8, 11),
                    "(11) 97142-8890", "brunocezar.oliveira@gmail.com", "Entorse de tornozelo direito grau II",
                    "Dor e inchaço no tornozelo direito; insegurança para correr e chutar.",
                    "Joga futebol amador aos fins de semana. Entorse em inversão durante um jogo há cinco semanas.",
                    "Nenhum.", "Sem saltos nem mudanças bruscas de direção até completar o treino proprioceptivo.",
                    "Ativo", Set.of(TUESDAY, FRIDAY), LocalTime.of(18, 0), 9, 0, 6, 17, 20, 6, 1, 45),
            new PacienteFicticio(1, "Amanda Lopes Ferraz", "996.556.492-29", "Feminino", LocalDate.of(1990, 1, 22),
                    "(11) 98819-4027", "amandalferraz@outlook.com", "Tendinopatia patelar (joelho direito)",
                    "Dor abaixo da patela ao agachar e ao subir escadas correndo.",
                    "Corredora de rua, treina para a primeira meia maratona; aumentou o volume de treino rápido demais.",
                    "Nenhum.", "Reduzir o volume de corrida pela metade até a dor ao agachar ceder.",
                    "Ativo", Set.of(MONDAY, WEDNESDAY), LocalTime.of(7, 0), 11, 0, 112, 134, 140, 5, 1, 50),
            new PacienteFicticio(1, "Diego Matsuda", "036.854.390-06", "Masculino", LocalDate.of(1988, 11, 30),
                    "(11) 99531-6604", "diego.matsuda@gmail.com", "Pós-operatório de luxação do ombro esquerdo",
                    "Ombro esquerdo rígido e medo de levantar o braço acima da cabeça.",
                    "Cirurgia de Bankart por artroscopia há nove semanas, depois da terceira luxação no jiu-jítsu.",
                    "Nenhum.", "Evitar rotação externa com o braço a 90° até a 12ª semana.",
                    "Ativo", Set.of(TUESDAY, THURSDAY), LocalTime.of(17, 0), 12, 0, 95, 150, 170, 6, 2, 50),
            // Sem telefone de propósito: nem todo paciente informa um, e é o
            // caso em que o lembrete no WhatsApp explica o que falta.
            new PacienteFicticio(1, "Vitória Sampaio", "889.030.728-55", "Feminino", LocalDate.of(2004, 4, 15),
                    null, "vitoria.sampaio04@gmail.com", "Entorse de punho direito",
                    "Dor no punho direito ao sacar e ao apoiar a mão no chão.",
                    "Joga vôlei no time da faculdade. Entorse ao cair sobre a mão num bloqueio.",
                    "Nenhum.", "Evitar apoio com o punho estendido até a dor ceder.",
                    "Inativo", Set.of(WEDNESDAY), LocalTime.of(18, 0), 6, 4, 38, 55, 70, 5, 2, 45),

            // --- Patrícia Yamamoto Reis, neurofuncional ---
            new PacienteFicticio(2, "Sebastião Ferreira Lima", "234.934.937-38", "Masculino", LocalDate.of(1951, 3, 8),
                    "(11) 99418-2736", null, "Rigidez de ombro pós-AVC",
                    "Braço direito pesado e sem força; precisa de ajuda para se vestir.",
                    "AVC isquêmico há cinco meses, com hemiparesia à direita. Hipertenso e diabético.",
                    "AAS 100 mg; sinvastatina 20 mg; losartana 50 mg; metformina 850 mg.",
                    "Não tracionar o braço afetado: risco de subluxação do ombro.",
                    "Ativo", Set.of(TUESDAY, FRIDAY), LocalTime.of(14, 0), 14, 0, 55, 88, 120, 4, 2, 60),
            new PacienteFicticio(2, "Neusa Maria Godoy", "198.585.666-20", "Feminino", LocalDate.of(1955, 12, 1),
                    "(11) 98724-5190", null, "Doença de Parkinson — rigidez e marcha",
                    "Passos curtos e arrastados; dificuldade para virar na cama.",
                    "Diagnóstico de doença de Parkinson há cinco anos. Uma queda em casa no último semestre.",
                    "Levodopa + carbidopa 250/25 mg, três vezes ao dia.",
                    "Atender no período \"on\" da medicação; atenção ao risco de queda.",
                    "Ativo", Set.of(MONDAY, THURSDAY), LocalTime.of(15, 0), 12, 0, 115, 138, 150, 3, 1, 50),
            new PacienteFicticio(2, "Henrique Batista Moraes", "516.547.888-21", "Masculino", LocalDate.of(1979, 6, 19),
                    "(11) 97368-0412", "henrique.bmoraes@yahoo.com.br", "Lesão do nervo radial (punho caído)",
                    "Não consegue levantar o punho nem esticar os dedos da mão direita.",
                    "Fratura da diáfise do úmero há dois meses, com compressão do nervo radial.",
                    "Complexo B; pregabalina 75 mg à noite.", "Usar a órtese de punho entre as sessões.",
                    "Ativo", Set.of(TUESDAY), LocalTime.of(16, 0), 8, 0, 8, 36, 60, 3, 1, 45),

            // --- Renato Aguiar Fonseca, gerontologia ---
            new PacienteFicticio(3, "Iracema dos Santos Pinto", "011.838.757-03", "Feminino", LocalDate.of(1943, 9, 14),
                    "(11) 99852-1167", null, "Pós-operatório de prótese de quadril",
                    "Medo de cair e dificuldade para levantar da cadeira e do vaso sanitário.",
                    "Prótese total do quadril direito há nove semanas, depois de uma fratura por queda. Osteoporose.",
                    "Alendronato 70 mg por semana; cálcio e vitamina D.",
                    "Evitar flexão do quadril acima de 90° e cruzar as pernas.",
                    "Ativo", Set.of(TUESDAY, FRIDAY), LocalTime.of(9, 0), 15, 0, 55, 88, 95, 6, 3, 50),
            new PacienteFicticio(3, "Osvaldo Rezende", "092.405.231-70", "Masculino", LocalDate.of(1947, 2, 26),
                    "(11) 98093-6648", null, "Artrose nos dois joelhos",
                    "Os joelhos doem ao caminhar até a padaria e ao subir a escada de casa.",
                    "Artrose nos dois joelhos, pior no direito. Hipertenso. Faz hidroginástica duas vezes por semana.",
                    "Losartana 50 mg; glucosamina e condroitina.", "Sem impacto; exercícios só em amplitude sem dor.",
                    "Ativo", Set.of(MONDAY, WEDNESDAY), LocalTime.of(10, 0), 11, 0, 95, 112, 120, 6, 3, 50),
            new PacienteFicticio(3, "Lourdes Helena Camargo", "833.398.742-71", "Feminino", LocalDate.of(1949, 5, 5),
                    "(11) 97615-3329", "lourdes.camargo49@gmail.com", "Fratura de punho — pós-imobilização",
                    "Punho esquerdo rígido depois de seis semanas de gesso.",
                    "Fratura do rádio distal numa queda na calçada, tratada com gesso. Mora com a filha.",
                    "Paracetamol 750 mg se houver dor; cálcio e vitamina D.",
                    "Sem carga no punho até a consolidação completa.",
                    "Alta", Set.of(THURSDAY), LocalTime.of(11, 0), 9, 2, 25, 62, 65, 5, 0, 45));

    private static final Roteiro ROTEIRO = new Roteiro(CLINICA, PROFISSIONAIS, PACIENTES, 3);

    private final GeradorDeClinica gerador;
    private final ManutencaoDaClinica manutencao;
    private final String senha;

    public ContaDeApresentacao(GeradorDeClinica gerador, ManutencaoDaClinica manutencao,
                               @Value("${rehabit.apresentacao.senha:}") String senha) {
        this.gerador = gerador;
        this.manutencao = manutencao;
        this.senha = senha;
    }

    /**
     * Não derruba a subida da API se algo der errado aqui: o site inteiro
     * sairia do ar por causa de uma conta. Cada passo tem a própria transação,
     * então o que falhar fica como estava, e a madrugada tenta de novo.
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            preparar();
        } catch (RuntimeException e) {
            log.error("Conta da apresentação não preparada; segue como estava.", e);
        }
    }

    /** Toda madrugada às 3h40 de Brasília, dez minutos depois da conferência da demonstração. */
    @Scheduled(cron = "0 40 3 * * *", zone = "America/Sao_Paulo")
    public void conferirDeMadrugada() {
        try {
            manterEmDia();
        } catch (RuntimeException e) {
            log.error("Conta da apresentação não atualizada; segue como estava.", e);
        }
    }

    /**
     * Na subida: cria a conta se ela ainda não existe, alinha a senha com a
     * variável e põe as fotos e os atendimentos em dia.
     */
    public void preparar() {
        criarSeNaoExistir();
        if (PoliticaDeSenha.problema(senha) == null) {
            manutencao.alinharSenhas(ROTEIRO, senha);
        }
        manterEmDia();
    }

    /** Fotos que faltam e atendimentos até ontem; nada é apagado. */
    public void manterEmDia() {
        manutencao.completarFotos(ROTEIRO);
        manutencao.seguirAtendendo(ROTEIRO);
    }

    /**
     * Não derruba a subida da API quando falta a senha: o site inteiro sairia
     * do ar por causa de uma conta. Avisa no log e segue sem ela.
     *
     * @return true se criou; false se a conta já existia ou se falta a senha.
     */
    public boolean criarSeNaoExistir() {
        String problema = PoliticaDeSenha.problema(senha);
        if (problema != null) {
            log.warn("Conta da apresentação não criada: defina REHABIT_APRESENTACAO_SENHA. {}", problema);
            return false;
        }
        return gerador.criarSeNaoExistir(ROTEIRO, senha);
    }
}
