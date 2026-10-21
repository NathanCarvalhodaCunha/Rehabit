package com.rehabit;

import com.rehabit.demo.ContaDeApresentacao;
import com.rehabit.demo.GeradorDeClinica;
import com.rehabit.demo.ManutencaoDaClinica;
import com.rehabit.model.Agendamento;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Notificacao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.ArquivoEnviadoRepository;
import com.rehabit.repository.NotificacaoRepository;
import com.rehabit.storage.EnvioDeFoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.TestPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A conta da apresentação no dia da banca: com as fotos da clínica e dos
 * profissionais, entrando com a senha da variável, e com o histórico chegando
 * até a véspera sem precisar recriar nada — inclusive quando a equipe já mexeu
 * nela pela tela.
 */
@TestPropertySource(properties = {"rehabit.apresentacao.ativar=true",
        "rehabit.apresentacao.senha=" + ApresentacaoProntaParaABancaTest.SENHA_DA_BANCA})
class ApresentacaoProntaParaABancaTest extends TesteDeIntegracao {

    static final String SENHA_DA_BANCA = "Banca-do-TCC-2026";

    /** Terça-feira, 20/10/2026, 10h em Brasília: uma semana antes da banca. */
    private static final Instant TERCA = Instant.parse("2026-10-20T13:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 20);
    private static final LocalDate BANCA = LocalDate.of(2026, 10, 27);
    private static final List<String> EMAILS = List.of(ContaDeApresentacao.EMAIL_CLINICA,
            "tccrehabit+mariana@gmail.com", "tccrehabit+lucas@gmail.com",
            "tccrehabit+patricia@gmail.com", "tccrehabit+renato@gmail.com");

    @TestConfiguration
    static class Relogio {
        @Bean
        @Primary
        RelogioDeTeste relogioDeTeste() {
            return new RelogioDeTeste(TERCA);
        }
    }

    @Autowired private ContaDeApresentacao apresentacao;
    @Autowired private GeradorDeClinica gerador;
    @Autowired private ManutencaoDaClinica manutencao;
    @Autowired private EnvioDeFoto envio;
    @Autowired private ArquivoEnviadoRepository enviados;
    @Autowired private NotificacaoRepository notificacoes;
    @Autowired private RelogioDeTeste relogio;
    @Value("${app.upload-dir}") private String pastaDeUploads;

    @BeforeEach
    void voltarParaTerca() {
        relogio.irPara(TERCA);
    }

    private void irParaODia(LocalDate dia) {
        relogio.irPara(Instant.parse(dia + "T13:00:00Z"));
    }

    private Clinica clinica() {
        return clinicas.findByEmail(ContaDeApresentacao.EMAIL_CLINICA).orElseThrow();
    }

    private Fisioterapeuta profissional(String apelido) {
        return fisioterapeutas.findByEmail("tccrehabit+" + apelido + "@gmail.com").orElseThrow();
    }

    private List<Fisioterapeuta> equipe() {
        return fisioterapeutas.findByIdClinicaOrderByNomeAsc(clinica().getId());
    }

    private Paciente paciente(String nome) {
        return pacientes.findByIdClinica(clinica().getId()).stream()
                .filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    private List<Sessao> sessoesDaClinica() {
        List<Integer> ids = pacientes.findByIdClinica(clinica().getId()).stream().map(Paciente::getId).toList();
        return sessoes.findByIdPacienteIn(ids);
    }

    private List<Agendamento> agendaDaClinica() {
        List<Integer> ids = pacientes.findByIdClinica(clinica().getId()).stream().map(Paciente::getId).toList();
        return agendamentos.findAll().stream().filter(a -> ids.contains(a.getIdPaciente())).toList();
    }

    private List<Agendamento> agendaDe(Paciente p) {
        return agendamentos.findByIdPacienteOrderByDataAgendamentoAscHoraAgendamentoAsc(p.getId());
    }

    private List<Sessao> sessoesDe(Paciente p) {
        return sessoes.findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(p.getId());
    }

    private Path arquivoDe(String url) {
        return Path.of(pastaDeUploads, url.substring("/uploads/".length()));
    }

    private Map<String, String> fotosPorEmail() {
        Map<String, String> fotos = equipe().stream()
                .collect(Collectors.toMap(Fisioterapeuta::getEmail, Fisioterapeuta::getFoto));
        fotos.put(clinica().getEmail(), clinica().getFoto());
        return fotos;
    }

    private int login(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    // ------------------------------------------------------------------ fotos

    @Test
    void asImagensDoJarSaoQuadradasEDoTamanhoDoMaiorCirculo() throws Exception {
        // O maior círculo do site é o do perfil (150 px), visto até em tela 3x.
        for (String nome : List.of("clinica", "mariana", "lucas", "patricia", "renato")) {
            try (InputStream entrada = new ClassPathResource("apresentacao/" + nome + ".png").getInputStream()) {
                BufferedImage imagem = ImageIO.read(entrada);
                assertThat(imagem.getWidth()).as(nome).isEqualTo(imagem.getHeight()).isGreaterThanOrEqualTo(450);
            }
        }
    }

    @Test
    void aContaNasceComFotoNaClinicaEEmCadaProfissional() throws Exception {
        apresentacao.preparar();

        List<String> fotos = new ArrayList<>(fotosPorEmail().values());
        assertThat(fotos).hasSize(5).doesNotContainNull().doesNotHaveDuplicates().allSatisfy(url -> {
            assertThat(url).matches("/uploads/[0-9a-f-]{36}\\.png");
            assertThat(arquivoDe(url)).exists();
        });
        // Servida como imagem e sem login, do jeito que o site a pede.
        mvc.perform(get(clinica().getFoto()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
        // O login devolve a foto: é ela que vai para o cabeçalho e o topo do celular.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"tccrehabit+mariana@gmail.com\",\"senha\":\"" + SENHA_DA_BANCA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.foto")
                        .value(profissional("mariana").getFoto()));
    }

    @Test
    void aContaCriadaAntesDasFotosGanhaAsFotosENadaMaisMuda() {
        // Como a conta que já existe em produção: criada sem fotos.
        assertThat(apresentacao.criarSeNaoExistir()).isTrue();
        assertThat(clinica().getFoto()).isNull();
        Integer id = clinica().getId();
        long sessoesAntes = sessoes.count();
        long agendaAntes = agendamentos.count();

        apresentacao.preparar();

        assertThat(clinica().getId()).isEqualTo(id);
        assertThat(fotosPorEmail().values()).hasSize(5).doesNotContainNull();
        assertThat(sessoes.count()).isEqualTo(sessoesAntes);
        assertThat(agendamentos.count()).isEqualTo(agendaAntes);
    }

    @Test
    void aFotoQueAEquipeTrocouFicaENadaSobeDeNovo() throws Exception {
        apresentacao.preparar();
        Fisioterapeuta mariana = profissional("mariana");
        String daEquipe = envio.salvar(Imagens.png(40, 40));
        mariana.setFoto(daEquipe);
        fisioterapeutas.save(mariana);
        Map<String, String> antes = fotosPorEmail();
        long enviosAntes = enviados.count();

        apresentacao.preparar();
        apresentacao.conferirDeMadrugada();

        assertThat(profissional("mariana").getFoto()).isEqualTo(daEquipe);
        assertThat(fotosPorEmail()).isEqualTo(antes);
        assertThat(enviados.count()).isEqualTo(enviosAntes);
    }

    @Test
    void aFotoCujoArquivoSumiuVoltaDoJar() throws Exception {
        // O disco do servidor é recriado a cada deploy quando não há Cloudinary.
        apresentacao.preparar();
        String antiga = profissional("lucas").getFoto();
        Files.delete(arquivoDe(antiga));

        apresentacao.preparar();

        String nova = profissional("lucas").getFoto();
        assertThat(nova).isNotEqualTo(antiga);
        assertThat(arquivoDe(nova)).exists();
    }

    @Test
    void aImagemDoJarPassaPeloMesmoFunilDasFotosDoSite() throws Exception {
        String url = envio.salvar(Imagens.jpegDeCelular(32, 24));

        byte[] gravado = Files.readAllBytes(arquivoDe(url));
        assertThat(new String(gravado, StandardCharsets.ISO_8859_1)).doesNotContain(Imagens.SEGREDO);
        // Entra na lista da LimpezaDeEnvios, que apaga se nenhum cadastro passar a usá-la.
        assertThat(enviados.findAll()).extracting(e -> e.getUrl()).contains(url);
    }

    // ------------------------------------------------------------------ senha

    @Test
    void asCincoContasEntramComASenhaDaVariavel() throws Exception {
        apresentacao.preparar();

        for (String email : EMAILS) {
            assertThat(login(email, SENHA_DA_BANCA)).as(email).isEqualTo(200);
        }
    }

    @Test
    void quandoAVariavelMudaASenhaDasCincoMudaEAsSessoesAntigasCaem() throws Exception {
        apresentacao.preparar();
        String sessaoDaClinica = tokenDe(clinica());
        String sessaoDaMariana = tokenDe(profissional("mariana"));

        new ContaDeApresentacao(gerador, manutencao, "Outra-Senha-Da-Banca-9").preparar();

        for (String email : EMAILS) {
            assertThat(login(email, "Outra-Senha-Da-Banca-9")).as(email).isEqualTo(200);
            assertThat(login(email, SENHA_DA_BANCA)).as(email).isEqualTo(401);
        }
        mvc.perform(get("/api/clinicas/" + clinica().getId()).header("Authorization", sessaoDaClinica))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fisioterapeutas/" + profissional("mariana").getId())
                        .header("Authorization", sessaoDaMariana))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void senhaTrocadaPelaTelaFicaEnquantoAVariavelNaoMuda() throws Exception {
        apresentacao.preparar();
        // A Mariana troca a senha pela tela (por exemplo, porque a da variável vazou).
        mvc.perform(put("/api/configuracoes/senha").header("Authorization", tokenDe(profissional("mariana")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + SENHA_DA_BANCA + "\",\"novaSenha\":\"Senha-Da-Mariana-77\"}"))
                .andExpect(status().isOk());

        apresentacao.preparar();
        apresentacao.conferirDeMadrugada();

        assertThat(login("tccrehabit+mariana@gmail.com", "Senha-Da-Mariana-77")).isEqualTo(200);
        assertThat(login("tccrehabit+mariana@gmail.com", SENHA_DA_BANCA)).isEqualTo(401);
        assertThat(login("tccrehabit+lucas@gmail.com", SENHA_DA_BANCA)).isEqualTo(200);

        // Quando a variável muda, ela vale de novo para as cinco.
        new ContaDeApresentacao(gerador, manutencao, "Outra-Senha-Da-Banca-9").preparar();
        for (String email : EMAILS) {
            assertThat(login(email, "Outra-Senha-Da-Banca-9")).as(email).isEqualTo(200);
        }
    }

    @Test
    void contaDeOutraClinicaComUmEmailDoRoteiroNaoEhTocada() throws Exception {
        apresentacao.preparar();
        // A clínica exclui o Renato pela tela: o e-mail dele fica livre...
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/fisioterapeutas/" + profissional("renato").getId())
                        .param("transferirPara", String.valueOf(profissional("mariana").getId()))
                        .header("Authorization", tokenDe(clinica())))
                .andExpect(status().is2xxSuccessful());
        // ...e outra clínica cadastra alguém com ele.
        Clinica outra = novaClinica();
        Fisioterapeuta deFora = novoProfissional(outra);
        deFora.setEmail("tccrehabit+renato@gmail.com");
        fisioterapeutas.save(deFora);

        new ContaDeApresentacao(gerador, manutencao, "Outra-Senha-Da-Banca-9").preparar();

        Fisioterapeuta depois = fisioterapeutas.findById(deFora.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(SENHA, depois.getSenha())).isTrue();
        assertThat(depois.getFoto()).isNull();
        assertThat(depois.getCredenciaisAlteradasEm()).isNull();
    }

    @Test
    void clinicaDeForaQueAdotouOEmailAntigoNaoEhTocada() throws Exception {
        apresentacao.preparar();
        Clinica alvorada = clinica();
        alvorada.setEmail("alvorada.nova@teste.rehabit");
        clinicas.save(alvorada);
        Clinica deFora = novaClinica();
        deFora.setEmail(ContaDeApresentacao.EMAIL_CLINICA);
        clinicas.save(deFora);

        new ContaDeApresentacao(gerador, manutencao, "Outra-Senha-Da-Banca-9").preparar();

        Clinica depois = clinicas.findById(deFora.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(SENHA, depois.getSenha())).isTrue();
        assertThat(depois.getFoto()).isNull();
        // A Alvorada, achada pelo CNPJ, recebeu a senha nova.
        assertThat(login("alvorada.nova@teste.rehabit", "Outra-Senha-Da-Banca-9")).isEqualTo(200);
    }

    @Test
    void subirDeNovoComAMesmaSenhaNaoDerrubaQuemEstaApresentando() throws Exception {
        apresentacao.preparar();
        String sessao = tokenDe(profissional("mariana"));

        apresentacao.preparar();

        mvc.perform(get("/api/fisioterapeutas/" + profissional("mariana").getId()).header("Authorization", sessao))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ atendimento

    @Test
    void noDiaDaBancaOHistoricoChegaAVesperaSemRecriarAConta() {
        apresentacao.preparar();
        Integer idClinica = clinica().getId();
        Map<String, Integer> idsDaEquipe = equipe().stream()
                .collect(Collectors.toMap(Fisioterapeuta::getEmail, Fisioterapeuta::getId));

        // Uma semana sem a API rodar de madrugada: a primeira subida põe tudo em dia.
        irParaODia(BANCA);
        apresentacao.preparar();

        assertThat(clinica().getId()).isEqualTo(idClinica);
        assertThat(equipe()).allSatisfy(f -> assertThat(f.getId()).isEqualTo(idsDaEquipe.get(f.getEmail())));
        List<LocalDate> datas = sessoesDaClinica().stream().map(Sessao::getDataSessao).toList();
        assertThat(datas).allSatisfy(d -> assertThat(d).isBefore(BANCA)).contains(BANCA.minusDays(1));
        for (LocalDate d = HOJE.plusDays(1); d.isBefore(BANCA); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                assertThat(datas).as("sessões em %s", d).contains(d);
            }
        }
        // Nada do que passou ficou "marcado"; o dia da banca segue com a agenda cheia.
        List<Agendamento> agenda = agendaDaClinica();
        assertThat(agenda).filteredOn(a -> a.getDataAgendamento().isBefore(BANCA))
                .extracting(Agendamento::getStatus).doesNotContain("AGENDADA", "REMARCADA");
        List<Agendamento> noDia = agenda.stream().filter(a -> a.getDataAgendamento().equals(BANCA)).toList();
        assertThat(noDia).hasSizeGreaterThanOrEqualTo(6)
                .allSatisfy(a -> assertThat(a.getStatus()).isIn("AGENDADA", "REMARCADA"))
                .anySatisfy(a -> assertThat(a.getHoraAgendamento()).isBefore(LocalTime.NOON))
                .anySatisfy(a -> assertThat(a.getHoraAgendamento()).isAfter(LocalTime.of(14, 0)));
        // E a agenda segue três semanas à frente.
        assertThat(agenda).extracting(Agendamento::getDataAgendamento)
                .anySatisfy(d -> assertThat(d).isBetween(BANCA.plusWeeks(2).plusDays(1), BANCA.plusWeeks(3)));
        // Cada profissional atendeu na semana da banca.
        Map<Integer, List<Sessao>> porProfissional = sessoesDaClinica().stream()
                .collect(Collectors.groupingBy(Sessao::getIdFisioterapeuta));
        assertThat(idsDaEquipe.values()).allSatisfy(id -> assertThat(porProfissional.get(id))
                .anySatisfy(s -> assertThat(s.getDataSessao()).isAfter(HOJE)));
    }

    @Test
    void umaMadrugadaPorVezChegaAoMesmoLugar() {
        apresentacao.preparar();

        for (LocalDate d = HOJE.plusDays(1); !d.isAfter(BANCA); d = d.plusDays(1)) {
            irParaODia(d);
            apresentacao.conferirDeMadrugada();
        }

        assertThat(sessoesDaClinica()).extracting(Sessao::getDataSessao)
                .allSatisfy(d -> assertThat(d).isBefore(BANCA)).contains(BANCA.minusDays(1));
        assertThat(agendaDaClinica()).filteredOn(a -> a.getDataAgendamento().isBefore(BANCA))
                .extracting(Agendamento::getStatus).doesNotContain("AGENDADA", "REMARCADA");
        // Um dia de consulta, uma consulta: nenhuma repetida no mesmo horário.
        assertThat(agendaDaClinica())
                .extracting(a -> a.getIdFisioterapeuta() + "|" + a.getDataAgendamento() + "|" + a.getHoraAgendamento())
                .doesNotHaveDuplicates();
        assertThat(sessoesDaClinica()).extracting(s -> s.getIdPaciente() + "|" + s.getDataSessao())
                .doesNotHaveDuplicates();
    }

    @Test
    void rodarDeNovoNoMesmoDiaNaoAtendeDeNovo() {
        apresentacao.preparar();
        irParaODia(LocalDate.of(2026, 10, 23));
        apresentacao.conferirDeMadrugada();
        long sessoesAntes = sessoes.count();
        long agendaAntes = agendamentos.count();
        long avisosAntes = notificacoes.count();

        apresentacao.conferirDeMadrugada();
        apresentacao.preparar();

        assertThat(sessoes.count()).isEqualTo(sessoesAntes);
        assertThat(agendamentos.count()).isEqualTo(agendaAntes);
        assertThat(notificacoes.count()).isEqualTo(avisosAntes);
    }

    @Test
    void oQueAEquipeFezPelaTelaFicaComoEsta() {
        apresentacao.preparar();
        LocalDate quarta = LocalDate.of(2026, 10, 21);
        LocalDate quinta = LocalDate.of(2026, 10, 22);
        LocalDate sexta = LocalDate.of(2026, 10, 23);
        // Marcou falta (Cláudia, segunda e quarta às 9h).
        Paciente claudia = paciente("Cláudia Regina Fontes");
        Agendamento falta = agendaDe(claudia).stream().filter(a -> a.getDataAgendamento().equals(quarta))
                .findFirst().orElseThrow();
        falta.setStatus("FALTOU");
        agendamentos.save(falta);
        // Cancelou uma consulta (Felipe, terça e quinta às 10h).
        Paciente felipe = paciente("Felipe Augusto Rangel");
        agendaDe(felipe).stream().filter(a -> a.getDataAgendamento().equals(quinta))
                .forEach(agendamentos::delete);
        // Registrou a sessão de verdade com o goniômetro, sem marcar presença (Sebastião, terça e sexta).
        Paciente sebastiao = paciente("Sebastião Ferreira Lima");
        Fisioterapeuta lucas = profissional("lucas");
        Sessao daEquipe = novaSessao(sebastiao, fisioterapeutas.findById(sebastiao.getIdFisioterapeuta()).orElseThrow(), sexta);
        // Cadastrou um paciente e marcou uma consulta para ele.
        Paciente novo = novoPaciente(lucas);
        Agendamento consultaDoNovo = novoAgendamento(novo, lucas, quinta, LocalTime.of(15, 30));

        irParaODia(LocalDate.of(2026, 10, 24));
        apresentacao.conferirDeMadrugada();

        assertThat(agendamentos.findById(falta.getId()).orElseThrow().getStatus()).isEqualTo("FALTOU");
        assertThat(sessoesDe(claudia)).extracting(Sessao::getDataSessao).doesNotContain(quarta);
        assertThat(agendaDe(felipe)).extracting(Agendamento::getDataAgendamento).doesNotContain(quinta);
        assertThat(sessoesDe(felipe)).extracting(Sessao::getDataSessao).doesNotContain(quinta);
        assertThat(agendaDe(sebastiao)).filteredOn(a -> a.getDataAgendamento().equals(sexta))
                .singleElement().extracting(Agendamento::getStatus).isEqualTo("REALIZADA");
        assertThat(sessoesDe(sebastiao)).filteredOn(s -> s.getDataSessao().equals(sexta))
                .singleElement().extracting(Sessao::getId).isEqualTo(daEquipe.getId());
        assertThat(agendamentos.findById(consultaDoNovo.getId()).orElseThrow().getStatus()).isEqualTo("AGENDADA");
        assertThat(sessoesDe(novo)).isEmpty();
    }

    @Test
    void pacienteQueAEquipeDeuAltaParaDeSerAtendido() {
        apresentacao.preparar();
        Paciente rodrigo = paciente("Rodrigo Tavares Mendes");
        rodrigo.setStatus("Alta");
        pacientes.save(rodrigo);
        LocalDate ultimaConsulta = agendaDe(rodrigo).get(agendaDe(rodrigo).size() - 1).getDataAgendamento();

        irParaODia(BANCA);
        apresentacao.conferirDeMadrugada();

        assertThat(sessoesDe(rodrigo)).extracting(Sessao::getDataSessao)
                .allSatisfy(d -> assertThat(d).isBefore(HOJE));
        assertThat(agendaDe(rodrigo)).filteredOn(a -> a.getDataAgendamento().isAfter(HOJE))
                .extracting(Agendamento::getStatus).doesNotContain("REALIZADA");
        assertThat(agendaDe(rodrigo).get(agendaDe(rodrigo).size() - 1).getDataAgendamento()).isEqualTo(ultimaConsulta);
    }

    @Test
    void asSessoesNovasSeguemACurvaDoPaciente() {
        apresentacao.preparar();
        Map<Integer, Sessao> ultimaAntes = sessoesDaClinica().stream().collect(Collectors.toMap(
                Sessao::getIdPaciente, Function.identity(),
                (a, b) -> a.getDataSessao().isAfter(b.getDataSessao()) ? a : b));

        irParaODia(BANCA);
        apresentacao.conferirDeMadrugada();

        List<Sessao> novas = sessoesDaClinica().stream().filter(s -> !s.getDataSessao().isBefore(HOJE)).toList();
        assertThat(novas).isNotEmpty().allSatisfy(s -> {
            Paciente p = pacientes.findById(s.getIdPaciente()).orElseThrow();
            double antes = medicoes.findByIdSessao(ultimaAntes.get(p.getId()).getId()).getAmplitudeMedia().doubleValue();
            Medicao m = medicoes.findByIdSessao(s.getId());
            assertThat(m.getAmplitudeMedia().doubleValue()).as(p.getNome())
                    .isBetween(antes - 4, p.getMetaAmplitude().doubleValue() + 2);
            assertThat(s.getDor()).isBetween(0, 10);
            assertThat(s.getDuracao()).isPositive();
            assertThat(m.getDataMedicao()).isEqualTo(s.getDataSessao());
            // Registrada ao fim da consulta daquele dia.
            Agendamento consulta = agendaDe(p).stream().filter(a -> a.getDataAgendamento().equals(s.getDataSessao()))
                    .findFirst().orElseThrow();
            assertThat(s.getHoraSessao()).isAfter(consulta.getHoraAgendamento());
            assertThat(consulta.getStatus()).isEqualTo("REALIZADA");
        });
    }

    @Test
    void oSinoAvisaAsSessoesNovasSemEncherDeNaoLidas() {
        apresentacao.preparar();

        // Uma semana de atendimentos de uma vez: dezenas de sessões novas.
        irParaODia(BANCA);
        apresentacao.conferirDeMadrugada();

        List<Notificacao> novas = notificacoes.findByIdClinicaOrderByCriadaEmDesc(clinica().getId()).stream()
                .filter(n -> !n.getCriadaEm().toLocalDate().isBefore(HOJE)).toList();
        assertThat(sessoesDaClinica()).filteredOn(s -> !s.getDataSessao().isBefore(HOJE)).hasSizeGreaterThan(8);
        // As mais recentes, como o sino da montagem: oito avisos, três não lidos.
        assertThat(novas).hasSize(8).allSatisfy(n -> {
            assertThat(n.getTipo()).isEqualTo("NOVA_SESSAO");
            assertThat(n.getMensagem()).startsWith("Nova sessão registrada para ");
        });
        assertThat(novas.subList(0, 3)).allSatisfy(n -> assertThat(n.isLida()).isFalse());
        assertThat(novas.subList(3, 8)).allSatisfy(n -> assertThat(n.isLida()).isTrue());
        assertThat(novas.get(0).getCriadaEm().toLocalDate()).isEqualTo(BANCA.minusDays(1));
    }

    @Test
    void aManutencaoNaoInventaRegressaoNaAmplitude() {
        apresentacao.preparar();

        for (LocalDate d = HOJE.plusDays(1); !d.isAfter(BANCA); d = d.plusDays(1)) {
            irParaODia(d);
            apresentacao.conferirDeMadrugada();
        }

        // A regra do alerta: as três medições mais recentes caindo duas vezes seguidas.
        for (Paciente p : pacientes.findByIdClinica(clinica().getId())) {
            List<Sessao> doPaciente = sessoesDe(p);
            if (doPaciente.isEmpty() || doPaciente.get(0).getDataSessao().isBefore(HOJE)) continue;
            List<java.math.BigDecimal> ultimas = doPaciente.stream().limit(3)
                    .map(s -> medicoes.findByIdSessao(s.getId()).getAmplitudeMedia()).toList();
            assertThat(ultimas.get(0).compareTo(ultimas.get(1)) < 0 && ultimas.get(1).compareTo(ultimas.get(2)) < 0)
                    .as("regressão inventada para %s: %s", p.getNome(), ultimas).isFalse();
        }
    }

    @Test
    void seAClinicaTrocarOEmailPelaTelaNadaQuebra() {
        apresentacao.preparar();
        Clinica alvorada = clinica();
        alvorada.setEmail("alvorada.nova@teste.rehabit");
        clinicas.save(alvorada);

        irParaODia(LocalDate.of(2026, 10, 23));
        assertThatCode(apresentacao::preparar).doesNotThrowAnyException();

        // Nenhuma clínica nova: a mesma, achada pelo CNPJ, seguiu atendendo.
        assertThat(clinicas.count()).isEqualTo(1);
        List<Integer> ids = pacientes.findByIdClinica(alvorada.getId()).stream().map(Paciente::getId).toList();
        assertThat(sessoes.findByIdPacienteIn(ids)).extracting(Sessao::getDataSessao)
                .contains(LocalDate.of(2026, 10, 22));
    }

    // ------------------------------------------------------------- operação

    @Test
    void conferePorContaPropriaTodaMadrugadaNoHorarioDeBrasilia() throws Exception {
        Scheduled agenda = ContaDeApresentacao.class.getMethod("conferirDeMadrugada").getAnnotation(Scheduled.class);
        assertThat(agenda).isNotNull();
        assertThat(agenda.cron()).isEqualTo("0 40 3 * * *");
        assertThat(agenda.zone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void seAManutencaoFalharAAPISobeMesmoAssim() {
        ManutencaoDaClinica comDefeito = Mockito.mock(ManutencaoDaClinica.class);
        Mockito.when(comDefeito.completarFotos(any())).thenThrow(new IllegalStateException("Cloudinary fora do ar"));
        ContaDeApresentacao conta = new ContaDeApresentacao(gerador, comDefeito, SENHA_DA_BANCA);

        assertThatCode(() -> conta.run(null)).doesNotThrowAnyException();
        assertThatCode(conta::conferirDeMadrugada).doesNotThrowAnyException();
        // A conta foi criada antes do passo que falhou.
        assertThat(clinicas.findByEmail(ContaDeApresentacao.EMAIL_CLINICA)).isPresent();
    }
}
