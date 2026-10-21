package com.rehabit;

import com.rehabit.demo.ContaDeApresentacao;
import com.rehabit.demo.ContaDeDemonstracao;
import com.rehabit.model.Agendamento;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A conta de demonstração é montada com as datas do dia, e ninguém mexe nela
 * (é só de leitura). Antes ela nascia uma vez e ficava: semanas depois, o
 * histórico parava no dia da montagem e a agenda era toda passado, com
 * consultas "marcadas" para dias que já tinham ido. Agora, quando a agenda
 * vira passado, ela é refeita com as datas do dia — sem tocar na conta da
 * apresentação nem em clínica nenhuma.
 */
@TestPropertySource(properties = {
        "rehabit.demo.ativar=true", "rehabit.demo.senha=senha-da-demo",
        "rehabit.apresentacao.ativar=true", "rehabit.apresentacao.senha=Banca-do-TCC-2026"})
class DemonstracaoSempreEmDiaTest extends TesteDeIntegracao {

    /** Terça-feira, 20/10/2026, 10h em Brasília. */
    private static final Instant TERCA = Instant.parse("2026-10-20T13:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 20);
    private static final List<String> A_VIR = List.of("AGENDADA", "REMARCADA");

    @TestConfiguration
    static class Relogio {
        @Bean
        @Primary
        RelogioDeTeste relogioDeTeste() {
            return new RelogioDeTeste(TERCA);
        }
    }

    @Autowired private ContaDeDemonstracao demonstracao;
    @Autowired private ContaDeApresentacao apresentacao;
    @Autowired private RelogioDeTeste relogio;

    @BeforeEach
    void voltarParaTerca() {
        relogio.irPara(TERCA);
    }

    private Clinica demo() {
        return clinicas.findByEmail(ContaDeDemonstracao.EMAIL_CLINICA).orElseThrow();
    }

    private List<Integer> idsDosPacientesDe(Clinica clinica) {
        return pacientes.findByIdClinica(clinica.getId()).stream().map(Paciente::getId).collect(Collectors.toList());
    }

    private List<Sessao> sessoesDe(Clinica clinica) {
        return sessoes.findByIdPacienteIn(idsDosPacientesDe(clinica));
    }

    private List<Agendamento> agendaDe(Clinica clinica) {
        List<Integer> ids = idsDosPacientesDe(clinica);
        return agendamentos.findAll().stream().filter(a -> ids.contains(a.getIdPaciente())).collect(Collectors.toList());
    }

    private void irParaODia(LocalDate dia) {
        relogio.irPara(Instant.parse(dia + "T13:00:00Z"));
    }

    @Test
    void noMesmoDiaNadaMuda() {
        assertThat(demonstracao.manterEmDia()).isTrue();
        Integer id = demo().getId();

        assertThat(demonstracao.manterEmDia()).isFalse();
        assertThat(demo().getId()).isEqualTo(id);
    }

    @Test
    void aMontadaSemanasAtrasComoADeProducaoERefeitaComOMesAtual() {
        // Montada em 05/10: duas semanas depois, a agenda inteira já passou.
        irParaODia(LocalDate.of(2026, 10, 5));
        demonstracao.manterEmDia();
        Integer antiga = demo().getId();
        assertThat(agendaDe(demo())).filteredOn(a -> "AGENDADA".equals(a.getStatus()))
                .allSatisfy(a -> assertThat(a.getDataAgendamento()).isBefore(HOJE));

        irParaODia(HOJE);
        assertThat(demonstracao.manterEmDia()).isTrue();

        Clinica nova = demo();
        assertThat(nova.getId()).isNotEqualTo(antiga);
        assertThat(clinicas.findById(antiga)).isEmpty();
        assertThat(fisioterapeutas.findByIdClinicaOrderByNomeAsc(nova.getId())).hasSize(5);
        assertThat(pacientes.findByIdClinica(nova.getId())).hasSize(16);

        // Sessões até ontem, com o mês corrente no histórico.
        List<Sessao> historico = sessoesDe(nova);
        assertThat(historico).allSatisfy(s -> assertThat(s.getDataSessao()).isBefore(HOJE));
        assertThat(historico).extracting(Sessao::getDataSessao)
                .contains(HOJE.minusDays(1))
                .anySatisfy(d -> assertThat(d).isBetween(LocalDate.of(2026, 10, 1), HOJE.minusDays(1)));
        // Consultas a partir de amanhã, cobrindo o resto do mês.
        List<Agendamento> agenda = agendaDe(nova);
        assertThat(agenda).filteredOn(a -> A_VIR.contains(a.getStatus()))
                .isNotEmpty()
                .allSatisfy(a -> assertThat(a.getDataAgendamento()).isAfter(HOJE));
        assertThat(agenda).extracting(Agendamento::getDataAgendamento)
                .anySatisfy(d -> assertThat(d).isBetween(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 31)));
        assertThat(agenda).extracting(Agendamento::getStatus).contains("AGENDADA", "REALIZADA", "FALTOU", "REMARCADA");
    }

    @Test
    void cadaProfissionalTemConsultasESessoesDoMes() {
        irParaODia(LocalDate.of(2026, 10, 5));
        demonstracao.manterEmDia();
        irParaODia(HOJE);
        demonstracao.manterEmDia();

        LocalDate inicioDoMes = HOJE.withDayOfMonth(1);
        LocalDate fimDoMes = HOJE.withDayOfMonth(HOJE.lengthOfMonth());
        List<Fisioterapeuta> equipe = fisioterapeutas.findByIdClinicaOrderByNomeAsc(demo().getId());
        assertThat(equipe).hasSize(5);
        for (Fisioterapeuta f : equipe) {
            // As mesmas consultas que a agenda do profissional pede à API:
            // a aba "Realizadas" (o que já passou) e a "Agendadas" (de hoje em diante).
            assertThat(agendamentos.findByIdFisioterapeutaAndDataAgendamentoLessThanOrderByDataAgendamentoDescHoraAgendamentoDesc(
                    f.getId(), HOJE))
                    .as("consultas realizadas de %s", f.getNome())
                    .anySatisfy(a -> {
                        assertThat(a.getStatus()).isEqualTo("REALIZADA");
                        assertThat(a.getDataAgendamento()).isBetween(inicioDoMes, HOJE.minusDays(1));
                    });
            assertThat(agendamentos.findByIdFisioterapeutaAndDataAgendamentoGreaterThanEqualOrderByDataAgendamentoAscHoraAgendamentoAsc(
                    f.getId(), HOJE))
                    .as("próximas consultas de %s", f.getNome())
                    .anySatisfy(a -> assertThat(a.getDataAgendamento()).isBetween(HOJE.plusDays(1), fimDoMes));
            assertThat(sessoes.findByIdFisioterapeuta(f.getId()))
                    .as("sessões de %s", f.getNome())
                    .anySatisfy(s -> assertThat(s.getDataSessao()).isBetween(inicioDoMes, HOJE.minusDays(1)));
        }
    }

    @Test
    void noDiaSeguinteARefaz() {
        demonstracao.manterEmDia();

        irParaODia(HOJE.plusDays(1)); // quarta, dia de consulta de vários pacientes
        assertThat(demonstracao.manterEmDia()).isTrue();

        assertThat(sessoesDe(demo())).extracting(Sessao::getDataSessao).contains(HOJE);
        assertThat(agendaDe(demo())).filteredOn(a -> A_VIR.contains(a.getStatus()))
                .allSatisfy(a -> assertThat(a.getDataAgendamento()).isAfter(HOJE.plusDays(1)));
    }

    @Test
    void aMontadaNaSextaValeAteSegunda() {
        LocalDate sexta = LocalDate.of(2026, 10, 23);
        irParaODia(sexta);
        demonstracao.manterEmDia();
        Integer id = demo().getId();

        // A agenda montada na sexta começa na segunda: sábado e domingo ela segue.
        irParaODia(sexta.plusDays(1));
        assertThat(demonstracao.manterEmDia()).isFalse();
        irParaODia(sexta.plusDays(2));
        assertThat(demonstracao.manterEmDia()).isFalse();
        assertThat(demo().getId()).isEqualTo(id);

        irParaODia(sexta.plusDays(3));
        assertThat(demonstracao.manterEmDia()).isTrue();
    }

    @Test
    void naoMexeNaApresentacaoNemNasOutrasClinicas() {
        demonstracao.manterEmDia();
        apresentacao.criarSeNaoExistir();
        Clinica alvorada = clinicas.findByEmail(ContaDeApresentacao.EMAIL_CLINICA).orElseThrow();
        long sessoesDaAlvorada = sessoesDe(alvorada).size();
        long agendaDaAlvorada = agendaDe(alvorada).size();
        Clinica comum = novaClinica();
        Fisioterapeuta profissional = novoProfissional(comum);
        Paciente paciente = novoPaciente(profissional);
        novaSessao(paciente, profissional, HOJE.minusDays(2));

        irParaODia(HOJE.plusDays(1));
        assertThat(demonstracao.manterEmDia()).isTrue();

        assertThat(clinicas.findByEmail(ContaDeApresentacao.EMAIL_CLINICA).orElseThrow().getId()).isEqualTo(alvorada.getId());
        assertThat(sessoesDe(alvorada)).hasSize((int) sessoesDaAlvorada);
        assertThat(agendaDe(alvorada)).hasSize((int) agendaDaAlvorada);
        assertThat(clinicas.findById(comum.getId())).isPresent();
        assertThat(pacientes.findById(paciente.getId())).isPresent();
        assertThat(sessoesDe(comum)).hasSize(1);
    }

    @Test
    void aSessaoDaContaAntigaCaiEOLoginContinuaOMesmo() throws Exception {
        demonstracao.manterEmDia();
        String tokenAntigo = tokenDe(demo());

        irParaODia(HOJE.plusDays(1));
        demonstracao.manterEmDia();

        mvc.perform(get("/api/clinicas/" + demo().getId()).header("Authorization", tokenAntigo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana.ribeiro@movimento.example\",\"senha\":\"senha-da-demo\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void seRefazerFalharAContaAntigaFicaEAAPISobeAssimMesmo() {
        irParaODia(LocalDate.of(2026, 10, 5));
        demonstracao.manterEmDia();
        Clinica antiga = demo();
        long sessoesDaAntiga = sessoesDe(antiga).size();
        // Outra clínica com o CNPJ que a conta nova vai usar: a montagem
        // falha no meio, depois de a antiga já ter sido apagada.
        antiga.setCnpj("11.222.333/0001-81");
        clinicas.save(antiga);
        Clinica outra = novaClinica();
        outra.setCnpj("47.315.268/0001-86");
        clinicas.save(outra);

        irParaODia(HOJE);
        assertThatThrownBy(() -> demonstracao.manterEmDia()).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> demonstracao.run(null)).doesNotThrowAnyException();

        // A transação desfez a exclusão: a conta antiga continua lá, inteira.
        assertThat(demo().getId()).isEqualTo(antiga.getId());
        assertThat(pacientes.findByIdClinica(antiga.getId())).hasSize(16);
        assertThat(sessoesDe(antiga)).hasSize((int) sessoesDaAntiga);
    }

    @Test
    void conferePorContaPropriaTodaMadrugadaNoHorarioDeBrasilia() throws Exception {
        Scheduled agenda = ContaDeDemonstracao.class.getMethod("conferirDeMadrugada").getAnnotation(Scheduled.class);
        assertThat(agenda).isNotNull();
        assertThat(agenda.cron()).isEqualTo("0 30 3 * * *");
        assertThat(agenda.zone()).isEqualTo("America/Sao_Paulo");
    }
}
