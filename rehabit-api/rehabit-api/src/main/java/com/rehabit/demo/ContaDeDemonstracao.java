package com.rehabit.demo;

import com.rehabit.demo.GeradorDeClinica.DadosDaClinica;
import com.rehabit.demo.GeradorDeClinica.PacienteFicticio;
import com.rehabit.demo.GeradorDeClinica.Profissional;
import com.rehabit.demo.GeradorDeClinica.Roteiro;
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
 * Conta de demonstração: uma clínica com cinco profissionais, dezesseis
 * pacientes e meses de atendimento, para apresentar e testar o Rehabit com
 * dados que se parecem com os de uma clínica de verdade. Quem monta a
 * clínica é o {@link GeradorDeClinica}; aqui fica só quem ela é.
 *
 * A Ana Paula Ribeiro é a conta que o botão "Entrar na demonstração" da
 * página inicial abre (Login/demonstracao.js), e as telas da página inicial
 * são dela. Ela tem foto — um retrato ilustrado, como os da conta da
 * apresentação (Avataaars, de Pablo Stanley, gerado com a DiceBear), em
 * src/main/resources/demonstracao —, posta pela {@link ManutencaoDaClinica}
 * depois de cada montagem: apagar a clínica leva as fotos junto.
 *
 * Sobe sempre (REHABIT_DEMO=false a desliga) e roda na subida da API e toda
 * madrugada. As datas são as do dia em que a conta é montada; quando a agenda
 * dela vira passado, a conta é refeita com as datas do dia (veja
 * GeradorDeClinica.refazerSeVencida), então a semana e o mês sempre têm
 * consultas, sessões e amplitude. Em dia, nada acontece. Como a conta é só de
 * leitura, refazê-la não apaga nada que alguém tenha feito nela.
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

    private static final DadosDaClinica CLINICA = new DadosDaClinica(
            "Clínica Movimento Fisioterapia", EMAIL_CLINICA, "47.315.268/0001-86", "(11) 3571-2040",
            "Rua Domingos de Morais, 1450 — Vila Mariana, São Paulo — SP", "Fisioterapia e reabilitação",
            "Clínica de reabilitação ortopédica, esportiva, neurológica e geriátrica, com "
                    + "acompanhamento da amplitude de movimento por goniômetro digital.",
            "São Paulo — SP");

    private static final List<Profissional> PROFISSIONAIS = List.of(
            new Profissional("Ana Paula Ribeiro", "ana.ribeiro@movimento.example", "CREFITO-3/123456-F",
                    "Ortopedia e traumatologia", "(11) 98765-4321",
                    "Fisioterapeuta há 12 anos, com foco em reabilitação pós-operatória de joelho e ombro.",
                    "demonstracao/ana.png"),
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

    private static final List<PacienteFicticio> PACIENTES = List.of(
            // --- Ana Paula Ribeiro, ortopedia ---
            new PacienteFicticio(0, "Marcos Vinícius Almeida", "482.917.305-04", "Masculino", LocalDate.of(1988, 4, 12),
                    "(11) 94127-3308", "marcos.almeida@email.example", "Pós-operatório de LCA (joelho direito)",
                    "Dificuldade para dobrar o joelho direito e para subir escadas depois da cirurgia.",
                    "Reconstrução do ligamento cruzado anterior com enxerto do tendão patelar. Sem outras cirurgias.",
                    "Paracetamol 750 mg se houver dor.",
                    "Evitar agachamento profundo e cadeia cinética aberta com carga até a 12ª semana.",
                    "Ativo", Set.of(MONDAY, THURSDAY), LocalTime.of(8, 0), 16, 0, 62, 118, 130, 7, 2, 50),
            new PacienteFicticio(0, "Helena Duarte Campos", "736.150.284-90", "Feminino", LocalDate.of(1956, 9, 3),
                    "(11) 99315-6072", "helena.campos@email.example", "Capsulite adesiva no ombro esquerdo",
                    "Não consegue levantar o braço esquerdo acima da cabeça; a dor piora à noite.",
                    "Diabetes tipo 2 controlada. Dor no ombro há quatro meses, sem trauma.",
                    "Metformina 850 mg; dipirona 1 g se houver dor.",
                    "Evitar mobilização vigorosa enquanto a dor noturna persistir.",
                    "Ativo", Set.of(TUESDAY, FRIDAY), LocalTime.of(9, 0), 14, 0, 78, 124, 160, 8, 4, 50),
            new PacienteFicticio(0, "Luíza Fernandes", "915.372.648-00", "Feminino", LocalDate.of(1979, 11, 25),
                    "(11) 98460-1195", "luiza.fernandes@email.example", "Epicondilite lateral (cotovelo direito)",
                    "Dor ao segurar objetos, torcer panos e digitar.",
                    "Analista administrativa, oito horas por dia ao computador.",
                    "Ibuprofeno 400 mg nas crises, por no máximo três dias.",
                    "Nenhuma relatada.",
                    "Ativo", Set.of(WEDNESDAY), LocalTime.of(14, 0), 8, 0, 45, 68, 70, 6, 2, 45),
            new PacienteFicticio(0, "Roberto Carlos Nunes", "264.839.150-98", "Masculino", LocalDate.of(1962, 6, 30),
                    "(11) 97218-4450", "roberto.nunes@email.example", "Artrose de quadril — pré-operatório",
                    "Dor ao caminhar mais de 15 minutos e ao calçar os sapatos.",
                    "Artroplastia total do quadril direito prevista para dezembro. Hipertenso.",
                    "Losartana 50 mg; paracetamol 750 mg.",
                    "Evitar flexão do quadril acima de 90° com rotação interna.",
                    "Ativo", Set.of(MONDAY, THURSDAY), LocalTime.of(10, 0), 10, 0, 72, 86, 100, 7, 5, 45),
            new PacienteFicticio(0, "Camila Rocha", "507.184.923-23", "Feminino", LocalDate.of(1995, 8, 8),
                    "(11) 99871-2036", "camila.rocha@email.example", "Tendinite do supraespinhal",
                    "Dor no ombro direito ao nadar crawl.",
                    "Nadadora amadora, treina quatro vezes por semana.",
                    "Nenhum.", "Nenhuma.",
                    "Alta", Set.of(TUESDAY), LocalTime.of(16, 0), 10, 3, 128, 172, 170, 5, 0, 45),

            // --- Rafael Moura, esportiva ---
            new PacienteFicticio(1, "Gustavo Lima Pereira", "839.261.574-37", "Masculino", LocalDate.of(1990, 3, 14),
                    "(11) 94552-8817", "gustavo.pereira@email.example", "Lesão de menisco medial (joelho esquerdo)",
                    "Travamento e dor no joelho esquerdo ao correr.",
                    "Corredor de rua, 40 km por semana. Lesão em treino de tiro.",
                    "Nenhum.", "Evitar rotação com o pé fixo e impacto até a liberação.",
                    "Ativo", Set.of(MONDAY, WEDNESDAY), LocalTime.of(7, 0), 12, 0, 95, 128, 135, 5, 1, 50),
            new PacienteFicticio(1, "Beatriz Monteiro", "173.548.296-09", "Feminino", LocalDate.of(1983, 12, 2),
                    "(11) 98033-4721", "beatriz.monteiro@email.example", "Pós-operatório de fratura do punho",
                    "Punho direito rígido e fraco depois de retirar a tala.",
                    "Fratura do rádio distal em queda de bicicleta; placa e parafusos.",
                    "Cálcio e vitamina D.", "Sem carga axial no punho até a 10ª semana.",
                    "Ativo", Set.of(TUESDAY, THURSDAY), LocalTime.of(17, 0), 11, 0, 32, 61, 70, 6, 2, 45),
            // Sem telefone de propósito: nem todo paciente informa um, e é o
            // caso em que o lembrete no WhatsApp explica o que falta.
            new PacienteFicticio(1, "Thiago Nascimento", "628.405.937-00", "Masculino", LocalDate.of(1998, 7, 21),
                    null, "thiago.nascimento@email.example", "Entorse de tornozelo grau II",
                    "Dor e insegurança no tornozelo esquerdo ao mudar de direção.",
                    "Jogador de futsal amador. Segunda entorse no mesmo tornozelo em um ano.",
                    "Nenhum.", "Nenhuma.",
                    "Ativo", Set.of(FRIDAY), LocalTime.of(18, 0), 7, 0, 8, 19, 20, 5, 1, 45),
            new PacienteFicticio(1, "Larissa Teixeira Gomes", "390.716.842-96", "Feminino", LocalDate.of(2002, 1, 30),
                    "(11) 99704-6653", "larissa.gomes@email.example", "Luxação recidivante de patela",
                    "Sensação de o joelho direito sair do lugar ao descer escadas.",
                    "Bailarina. Dois episódios de luxação da patela direita no último ano.",
                    "Nenhum.", "Evitar valgo dinâmico e saltos até completar o fortalecimento.",
                    "Inativo", Set.of(WEDNESDAY), LocalTime.of(8, 0), 6, 5, 110, 126, 140, 4, 2, 45),

            // --- Juliana Castro, neurofuncional ---
            new PacienteFicticio(2, "Antônio José Barbosa", "451.829.063-15", "Masculino", LocalDate.of(1949, 5, 19),
                    "(11) 99402-3158", null, "Rigidez de ombro pós-AVC",
                    "Pouca mobilidade no braço esquerdo; dificuldade para se vestir sozinho.",
                    "AVC isquêmico há quatro meses, hemiparesia à esquerda. Hipertenso e diabético.",
                    "AAS 100 mg; atorvastatina 20 mg; losartana 50 mg.",
                    "Cuidado com subluxação do ombro: não tracionar o membro afetado.",
                    "Ativo", Set.of(TUESDAY, FRIDAY), LocalTime.of(14, 0), 13, 0, 58, 84, 120, 4, 3, 60),
            new PacienteFicticio(2, "Sônia Regina Prado", "814.637.205-80", "Feminino", LocalDate.of(1958, 10, 11),
                    "(11) 99128-5540", "sonia.prado@email.example", "Parkinson — rigidez e marcha",
                    "Passos curtos, dificuldade para levantar o braço e para virar na cama.",
                    "Diagnóstico de doença de Parkinson há seis anos.",
                    "Levodopa + benserazida 200/50 mg, três vezes ao dia.",
                    "Atender no período \"on\" da medicação; atenção ao risco de queda.",
                    "Ativo", Set.of(MONDAY, THURSDAY), LocalTime.of(15, 0), 12, 0, 118, 136, 150, 2, 1, 50),
            new PacienteFicticio(2, "Eduardo Martins Leal", "297.053.418-50", "Masculino", LocalDate.of(1976, 2, 8),
                    "(11) 98247-3319", "eduardo.leal@email.example", "Paralisia do nervo radial (punho caído)",
                    "Não consegue levantar o punho direito.",
                    "Compressão do nervo radial depois de dormir com o braço apoiado, há sete semanas.",
                    "Complexo B.", "Usar a órtese de repouso entre as sessões.",
                    "Ativo", Set.of(WEDNESDAY), LocalTime.of(11, 0), 7, 0, 5, 38, 60, 1, 0, 45),

            // --- Carlos Eduardo Tanaka, reumatologia ---
            new PacienteFicticio(3, "Maria Aparecida Souza", "685.190.374-10", "Feminino", LocalDate.of(1967, 4, 2),
                    "(11) 97611-0284", "maria.souza@email.example", "Artrite reumatoide — mãos e punhos",
                    "Rigidez nas mãos por mais de uma hora ao acordar.",
                    "Artrite reumatoide há nove anos, acompanhada por reumatologista.",
                    "Metotrexato 15 mg por semana; ácido fólico.",
                    "Evitar exercício resistido em articulação com sinovite ativa.",
                    "Ativo", Set.of(TUESDAY, THURSDAY), LocalTime.of(10, 0), 12, 0, 40, 58, 65, 6, 3, 50),
            new PacienteFicticio(3, "José Ricardo Farias", "342.876.519-28", "Masculino", LocalDate.of(1971, 9, 17),
                    "(11) 99463-7702", "jose.farias@email.example", "Espondilite anquilosante",
                    "Dor lombar que melhora com movimento e piora em repouso.",
                    "HLA-B27 positivo. Diagnóstico há três anos.",
                    "Adalimumabe 40 mg a cada 15 dias.", "Evitar flexão forçada da coluna.",
                    "Ativo", Set.of(MONDAY), LocalTime.of(17, 0), 9, 0, 88, 104, 110, 5, 3, 50),

            // --- Fernanda Oliveira Lima, gerontologia ---
            new PacienteFicticio(4, "Terezinha de Jesus Almeida", "956.314.827-46", "Feminino", LocalDate.of(1941, 12, 24),
                    "(11) 97736-5014", null, "Pós-operatório de prótese de joelho",
                    "Dificuldade para levantar da cadeira e caminhar sem o andador.",
                    "Artroplastia total do joelho esquerdo há oito semanas. Osteoporose.",
                    "Alendronato 70 mg por semana; cálcio e vitamina D.",
                    "Risco de queda: exercícios sempre com apoio e sem impacto.",
                    "Ativo", Set.of(MONDAY, THURSDAY), LocalTime.of(11, 0), 14, 0, 70, 102, 110, 6, 3, 50),
            new PacienteFicticio(4, "Walter Hideki Sato", "128.590.643-89", "Masculino", LocalDate.of(1945, 3, 6),
                    "(11) 98815-2267", "walter.sato@email.example", "Fratura de úmero proximal",
                    "Ombro direito rígido depois de uma queda em casa.",
                    "Fratura do úmero proximal tratada com tipoia por quatro semanas. Mora sozinho.",
                    "Paracetamol 750 mg se houver dor.",
                    "Sem carga no membro superior direito até a consolidação completa.",
                    "Ativo", Set.of(TUESDAY, FRIDAY), LocalTime.of(8, 0), 10, 0, 60, 98, 130, 5, 3, 45));

    private static final Roteiro ROTEIRO = new Roteiro(CLINICA, PROFISSIONAIS, PACIENTES, 2);

    private final GeradorDeClinica gerador;
    private final ManutencaoDaClinica manutencao;
    private final String senha;

    public ContaDeDemonstracao(GeradorDeClinica gerador, ManutencaoDaClinica manutencao,
                               @Value("${rehabit.demo.senha}") String senha) {
        this.gerador = gerador;
        this.manutencao = manutencao;
        this.senha = senha;
    }

    /**
     * Não derruba a subida da API se refazer a conta falhar: o site inteiro
     * sairia do ar por causa de uma conta de exemplo. A transação desfaz o
     * que tinha sido apagado, a conta antiga fica e a madrugada tenta de novo.
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            manterEmDia();
        } catch (RuntimeException e) {
            log.error("Conta de demonstração não atualizada; segue a que já existia.", e);
        }
    }

    /**
     * A API pode ficar dias sem reiniciar: de madrugada, no horário de
     * Brasília, a conta confere as datas de novo, para amanhecer em dia.
     */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    public void conferirDeMadrugada() {
        manterEmDia();
    }

    /**
     * Refaz a conta se as datas dela ficaram para trás e põe a foto da Ana
     * onde ela falta — numa conta refeita, sempre.
     *
     * @return true se criou ou refez a conta; false se ela já estava em dia.
     */
    public boolean manterEmDia() {
        boolean refeita = gerador.refazerSeVencida(ROTEIRO, senha);
        manutencao.completarFotos(ROTEIRO);
        return refeita;
    }

    /** @return true se criou; false se a conta já existia, em dia ou não. */
    public boolean criarSeNaoExistir() {
        return gerador.criarSeNaoExistir(ROTEIRO, senha);
    }
}
