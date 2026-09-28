package com.rehabit;

import com.rehabit.model.Agendamento;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.AgendamentoRepository;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import com.rehabit.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base dos testes que sobem a aplicação inteira contra um H2 em memória (veja
 * src/test/resources/application.properties) e falam com ela por HTTP, do
 * mesmo jeito que o site fala.
 *
 * Os dados são montados direto pelos repositórios, e não pelas rotas de
 * cadastro: o que está em teste é a exclusão, e passar pelo cadastro traria
 * junto validação de e-mail, DNS e regras que não têm nada a ver com ela.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class TesteDeIntegracao {

    protected static final String SENHA = "senha-de-teste";

    // Cada chamada gera valores únicos para os campos UNIQUE (e-mail, CNPJ,
    // CPF, COFFITO), senão dois profissionais do mesmo teste colidiriam.
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected JwtService jwtService;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected ApplicationContext contexto;

    @Autowired protected ClinicaRepository clinicas;
    @Autowired protected FisioterapeutaRepository fisioterapeutas;
    @Autowired protected PacienteRepository pacientes;
    @Autowired protected SessaoRepository sessoes;
    @Autowired protected MedicaoRepository medicoes;
    @Autowired protected AgendamentoRepository agendamentos;

    /**
     * Zera todas as tabelas antes de cada teste. Pega todos os repositórios do
     * contexto, e não uma lista fixa, para que uma tabela nova entre na
     * limpeza sozinha.
     */
    @BeforeEach
    void zerarBanco() {
        contexto.getBeansOfType(JpaRepository.class).values().forEach(JpaRepository::deleteAllInBatch);
    }

    protected static int proximo() {
        return SEQUENCIA.incrementAndGet();
    }

    protected Clinica novaClinica() {
        int n = proximo();
        Clinica c = new Clinica();
        c.setNome("Clínica " + n);
        c.setEmail("clinica" + n + "@teste.rehabit");
        c.setCnpj(String.format("%014d", n));
        c.setSenha(passwordEncoder.encode(SENHA));
        return clinicas.save(c);
    }

    protected Fisioterapeuta novoProfissional(Clinica clinica) {
        int n = proximo();
        Fisioterapeuta f = new Fisioterapeuta();
        f.setNome("Profissional " + n);
        f.setEmail("profissional" + n + "@teste.rehabit");
        f.setCoffito("CREFITO-" + n);
        f.setSenha(passwordEncoder.encode(SENHA));
        f.setIdClinica(clinica.getId());
        return fisioterapeutas.save(f);
    }

    protected Paciente novoPaciente(Fisioterapeuta responsavel) {
        int n = proximo();
        Paciente p = new Paciente();
        p.setNome("Paciente " + n);
        p.setCpf(String.format("%011d", n));
        p.setStatus("Ativo");
        p.setIdClinica(responsavel.getIdClinica());
        p.setIdFisioterapeuta(responsavel.getId());
        return pacientes.save(p);
    }

    /** Sessão já realizada, atribuída a quem atendeu, com a sua medição. */
    protected Sessao novaSessao(Paciente paciente, Fisioterapeuta quemAtendeu, LocalDate data) {
        Sessao s = new Sessao();
        s.setIdPaciente(paciente.getId());
        s.setIdFisioterapeuta(quemAtendeu.getId());
        s.setDataSessao(data);
        s.setHoraSessao(LocalTime.of(9, 0));
        s.setDuracao(45);
        Sessao salva = sessoes.save(s);

        Medicao m = new Medicao();
        m.setIdSessao(salva.getId());
        m.setAmplitudeMedia(new BigDecimal("90.00"));
        m.setDataMedicao(data);
        medicoes.save(m);
        return salva;
    }

    protected Agendamento novoAgendamento(Paciente paciente, Fisioterapeuta profissional,
                                          LocalDate data, LocalTime hora) {
        Agendamento a = new Agendamento();
        a.setIdPaciente(paciente.getId());
        a.setIdFisioterapeuta(profissional.getId());
        a.setDataAgendamento(data);
        a.setHoraAgendamento(hora);
        a.setStatus("AGENDADA");
        return agendamentos.save(a);
    }

    protected String tokenDe(Clinica clinica) {
        return "Bearer " + jwtService.gerarToken(clinica.getId(), "CLINICA");
    }

    protected String tokenDe(Fisioterapeuta fisioterapeuta) {
        return "Bearer " + jwtService.gerarToken(fisioterapeuta.getId(), "FISIOTERAPEUTA");
    }
}
