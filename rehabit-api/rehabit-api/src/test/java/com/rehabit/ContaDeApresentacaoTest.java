package com.rehabit;

import com.rehabit.demo.ContaDeApresentacao;
import com.rehabit.demo.ContaDeDemonstracao;
import com.rehabit.demo.GeradorDeClinica;
import com.rehabit.demo.ManutencaoDaClinica;
import com.rehabit.email.ValidadorEmailService;
import com.rehabit.model.Agendamento;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"rehabit.apresentacao.ativar=true",
        "rehabit.apresentacao.senha=senha-da-apresentacao"})
class ContaDeApresentacaoTest extends TesteDeIntegracao {

    /** Terça-feira, 13/10/2026, 10h em Brasília: duas semanas antes da apresentação. */
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 13);
    private static final LocalDate DIA_DA_APRESENTACAO = LocalDate.of(2026, 10, 27);

    @TestConfiguration
    static class RelogioParado {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return Clock.fixed(Instant.parse("2026-10-13T13:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired ContaDeApresentacao apresentacao;
    @Autowired GeradorDeClinica gerador;
    @Autowired ManutencaoDaClinica manutencao;
    @Autowired ValidadorEmailService validadorEmail;

    @Test
    void criaAContaUmaVezSo() {
        // A base zera o banco antes de cada teste, apagando o que a subida criou.
        assertThat(apresentacao.criarSeNaoExistir()).isTrue();
        assertThat(apresentacao.criarSeNaoExistir()).isFalse();

        assertThat(clinicas.count()).isEqualTo(1);
        assertThat(fisioterapeutas.count()).isEqualTo(4);
        assertThat(pacientes.count()).isEqualTo(14);
    }

    @Test
    void semSenhaOuComSenhaFracaNaoCriaNada() {
        assertThat(new ContaDeApresentacao(gerador, manutencao, "").criarSeNaoExistir()).isFalse();
        assertThat(new ContaDeApresentacao(gerador, manutencao, "123456").criarSeNaoExistir()).isFalse();

        assertThat(clinicas.count()).isZero();
    }

    @Test
    void aClinicaEOsProfissionaisEntramComASenhaConfigurada() throws Exception {
        apresentacao.criarSeNaoExistir();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ContaDeApresentacao.EMAIL_CLINICA
                                + "\",\"senha\":\"senha-da-apresentacao\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CLINICA"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mariana.bertolini@alvorada.example\",\"senha\":\"senha-da-apresentacao\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("FISIOTERAPEUTA"))
                .andExpect(jsonPath("$.nome").value("Mariana Costa Bertolini"));
    }

    @Test
    void naoEhSoDeLeituraComoADemonstracao() throws Exception {
        apresentacao.criarSeNaoExistir();
        Fisioterapeuta mariana = fisioterapeutas.findByEmail("mariana.bertolini@alvorada.example").orElseThrow();
        Paciente paciente = pacientes.findAll().stream()
                .filter(p -> p.getIdFisioterapeuta().equals(mariana.getId()))
                .findFirst().orElseThrow();

        mvc.perform(put("/api/pacientes/" + paciente.getId())
                        .header("Authorization", tokenDe(mariana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome alterado na apresentação\"}"))
                .andExpect(status().isOk());

        assertThat(pacientes.findById(paciente.getId()).orElseThrow().getNome())
                .isEqualTo("Nome alterado na apresentação");
    }

    @Test
    void osEmailsSaoDoDominioReservadoSemOMais() {
        apresentacao.criarSeNaoExistir();

        List<String> emails = Stream.concat(
                clinicas.findAll().stream().map(Clinica::getEmail),
                fisioterapeutas.findAll().stream().map(Fisioterapeuta::getEmail)).toList();
        assertThat(emails).hasSize(5).doesNotHaveDuplicates().allSatisfy(email -> {
            // Como os da demonstração: .example não entrega e-mail a ninguém.
            assertThat(email).matches("[a-z]+(\\.[a-z]+)?@alvorada\\.example");
            // O mesmo endereço que o login monta a partir do que se digita.
            assertThat(validadorEmail.normalizar(email)).isEqualTo(email);
        });
    }

    @Test
    void aAgendaChegaAoDiaDaApresentacaoSemConflito() {
        apresentacao.criarSeNaoExistir();

        List<Agendamento> agenda = agendamentos.findAll();
        Set<String> ocupados = new HashSet<>();
        for (Agendamento a : agenda) {
            String chave = a.getIdFisioterapeuta() + "|" + a.getDataAgendamento() + "|" + a.getHoraAgendamento();
            assertThat(ocupados.add(chave)).as("horário repetido: %s", chave).isTrue();
            if ("REALIZADA".equals(a.getStatus()) || "FALTOU".equals(a.getStatus())) {
                assertThat(a.getDataAgendamento()).isBefore(HOJE);
            } else {
                assertThat(a.getDataAgendamento()).isAfter(HOJE);
            }
        }

        // Há paciente marcado de manhã e à tarde no dia, seja qual for o horário da banca.
        List<LocalTime> noDia = agenda.stream()
                .filter(a -> a.getDataAgendamento().equals(DIA_DA_APRESENTACAO))
                .map(Agendamento::getHoraAgendamento)
                .toList();
        assertThat(noDia).hasSizeGreaterThanOrEqualTo(6)
                .anyMatch(h -> h.isBefore(LocalTime.NOON))
                .anyMatch(h -> h.isAfter(LocalTime.of(14, 0)));
    }

    @Test
    void convivemNoMesmoBancoComADemonstracao() {
        // No Render as duas podem estar ligadas: CPF, CNPJ, CREFITO e e-mail
        // são únicos no banco, e uma colisão faria a segunda subida falhar.
        apresentacao.criarSeNaoExistir();
        assertThat(new ContaDeDemonstracao(gerador, manutencao, "senha-da-demo").criarSeNaoExistir()).isTrue();

        assertThat(clinicas.count()).isEqualTo(2);
    }

    @Test
    void osCpfsTemDigitosVerificadoresValidos() {
        apresentacao.criarSeNaoExistir();

        assertThat(pacientes.findAll()).extracting(Paciente::getCpf)
                .allSatisfy(cpf -> assertThat(cpfValido(cpf)).as(cpf).isTrue());
    }

    private static boolean cpfValido(String cpf) {
        String d = cpf.replaceAll("\\D", "");
        if (d.length() != 11 || d.chars().distinct().count() == 1) return false;
        for (int n = 9; n <= 10; n++) {
            int soma = 0;
            for (int i = 0; i < n; i++) soma += (d.charAt(i) - '0') * (n + 1 - i);
            int digito = soma * 10 % 11 % 10;
            if (digito != d.charAt(n) - '0') return false;
        }
        return true;
    }
}
