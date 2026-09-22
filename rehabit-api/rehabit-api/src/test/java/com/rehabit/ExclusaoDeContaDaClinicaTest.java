package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.CodigoPareamento;
import com.rehabit.model.Configuracao;
import com.rehabit.model.Dispositivo;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Goniometro;
import com.rehabit.model.Notificacao;
import com.rehabit.model.Paciente;
import com.rehabit.model.RecuperacaoSenha;
import com.rehabit.model.VerificacaoEmail;
import com.rehabit.repository.CodigoPareamentoRepository;
import com.rehabit.repository.ConfiguracaoRepository;
import com.rehabit.repository.DispositivoRepository;
import com.rehabit.repository.GoniometroRepository;
import com.rehabit.repository.NotificacaoRepository;
import com.rehabit.repository.RecuperacaoSenhaRepository;
import com.rehabit.repository.VerificacaoEmailRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.MediaType;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A clínica exclui a própria conta: tudo que pertence a ela é apagado de
 * verdade, e nada de outra clínica é tocado.
 */
class ExclusaoDeContaDaClinicaTest extends TesteDeIntegracao {

    /**
     * Toda entidade que existe hoje. Se aparecer uma nova, o teste
     * {@link #nenhumaEntidadeNovaFicaDeForaDaExclusao} quebra de propósito.
     */
    private static final Set<String> ENTIDADES_CONHECIDAS = Set.of(
            "Agendamento", "Clinica", "CodigoPareamento", "Configuracao", "Dispositivo",
            "Fisioterapeuta", "Goniometro", "Medicao", "Notificacao", "Paciente",
            "RecuperacaoSenha", "Sessao", "VerificacaoEmail");

    @Autowired private EntityManager entityManager;
    @Autowired private ConfiguracaoRepository configuracoes;
    @Autowired private RecuperacaoSenhaRepository recuperacoes;
    @Autowired private VerificacaoEmailRepository verificacoes;
    @Autowired private NotificacaoRepository notificacoes;
    @Autowired private DispositivoRepository dispositivos;
    @Autowired private GoniometroRepository goniometros;
    @Autowired private CodigoPareamentoRepository codigosDePareamento;

    @Value("${app.upload-dir}")
    private String pastaDeUploads;

    @AfterEach
    void limparFotos() throws IOException {
        FileSystemUtils.deleteRecursively(Path.of(pastaDeUploads));
    }

    // ------------------------------------------------------------------
    // Montagem
    // ------------------------------------------------------------------

    private String corpoComSenha(String senha) {
        return "{\"senha\":\"" + senha + "\"}";
    }

    /** Uma foto de verdade no disco, como o upload local deixaria. */
    private String fotoNoDisco() throws IOException {
        Path pasta = Path.of(pastaDeUploads).toAbsolutePath();
        Files.createDirectories(pasta);
        String nome = "foto-" + proximo() + ".png";
        Files.writeString(pasta.resolve(nome), "imagem");
        return "/uploads/" + nome;
    }

    private boolean existeNoDisco(String url) {
        return Files.exists(Path.of(pastaDeUploads).toAbsolutePath().resolve(url.substring("/uploads/".length())));
    }

    /**
     * Uma clínica com uma linha em cada tabela que pode pertencer a ela —
     * inclusive um profissional já excluído, que continua existindo no banco.
     */
    private Clinica clinicaCompleta() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        Fisioterapeuta carlos = novoProfissional(clinica);
        Paciente joao = novoPaciente(ana);
        novaSessao(joao, ana, LocalDate.now().minusDays(3));
        novoAgendamento(joao, ana, LocalDate.now().plusDays(3), LocalTime.of(10, 0));

        configuracaoDe("CLINICA", clinica.getId());
        configuracaoDe("FISIOTERAPEUTA", ana.getId());
        pedidoDeRecuperacao(clinica.getEmail(), "CLINICA");
        pedidoDeRecuperacao(ana.getEmail(), "FISIOTERAPEUTA");
        verificacaoDeEmail(clinica.getEmail());

        Notificacao n = new Notificacao();
        n.setIdClinica(clinica.getId());
        n.setTipo("NOVA_SESSAO");
        n.setMensagem("Nova sessão");
        n.setCriadaEm(LocalDateTime.now());
        notificacoes.save(n);

        Dispositivo d = new Dispositivo();
        d.setIdClinica(clinica.getId());
        d.setNome("Goniômetro da sala 1");
        d.setAtivo(true);
        d.setCriadoEm(LocalDateTime.now());
        dispositivos.save(d);

        Goniometro g = new Goniometro();
        g.setIdClinica(clinica.getId());
        goniometros.save(g);

        CodigoPareamento c = new CodigoPareamento();
        c.setIdClinica(clinica.getId());
        c.setCodigo(String.format("%06d", proximo()));
        c.setExpiraEm(LocalDateTime.now().plusMinutes(10));
        codigosDePareamento.save(c);

        // Carlos sai antes da clínica: a linha dele fica, marcada como excluída.
        mvc.perform(delete("/api/fisioterapeutas/{id}", carlos.getId())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk());
        return clinica;
    }

    private void configuracaoDe(String tipo, Integer id) {
        Configuracao c = new Configuracao();
        c.setTipoUsuario(tipo);
        c.setIdUsuario(id);
        configuracoes.save(c);
    }

    private void pedidoDeRecuperacao(String email, String tipo) {
        RecuperacaoSenha r = new RecuperacaoSenha();
        r.setEmail(email);
        r.setTipoConta(tipo);
        r.setTokenHash("hash-" + proximo());
        r.setCodigoHash("hash");
        r.setCriadoEm(LocalDateTime.now());
        r.setExpiraEm(LocalDateTime.now().plusMinutes(30));
        recuperacoes.save(r);
    }

    private void verificacaoDeEmail(String email) {
        VerificacaoEmail v = new VerificacaoEmail();
        v.setEmail(email);
        v.setCodigoHash("hash");
        v.setCriadoEm(LocalDateTime.now());
        v.setExpiraEm(LocalDateTime.now().plusMinutes(15));
        verificacoes.save(v);
    }

    /** Quantas linhas cada tabela tem agora, pelo nome da entidade. */
    private Map<String, Long> linhasPorTabela() {
        Map<String, Long> contagem = new TreeMap<>();
        contexto.getBeansOfType(JpaRepository.class).forEach((nome, repositorio) -> contagem.put(nome, repositorio.count()));
        return contagem;
    }

    // ------------------------------------------------------------------
    // Resumo mostrado no modal
    // ------------------------------------------------------------------

    @Test
    void oResumoContaOQueVaiSerApagado() throws Exception {
        Clinica clinica = clinicaCompleta();

        mvc.perform(get("/api/clinicas/{id}/exclusao", clinica.getId()).header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profissionais").value(1))
                .andExpect(jsonPath("$.pacientes").value(1))
                .andExpect(jsonPath("$.sessoes").value(1))
                .andExpect(jsonPath("$.agendamentos").value(1))
                .andExpect(jsonPath("$.dispositivos").value(1));
    }

    // ------------------------------------------------------------------
    // Quem pode e quem não pode
    // ------------------------------------------------------------------

    @Test
    void senhaErradaRecusaENadaEApagado() throws Exception {
        Clinica clinica = clinicaCompleta();
        Map<String, Long> antes = linhasPorTabela();

        mvc.perform(post("/api/clinicas/{id}/exclusao", clinica.getId())
                        .header(AUTHORIZATION, tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComSenha("senha-errada")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("Senha incorreta."));

        assertThat(linhasPorTabela()).isEqualTo(antes);
    }

    @Test
    void outraClinicaNaoPodeExcluir() throws Exception {
        Clinica clinica = clinicaCompleta();
        Clinica intrusa = novaClinica();

        mvc.perform(post("/api/clinicas/{id}/exclusao", clinica.getId())
                        .header(AUTHORIZATION, tokenDe(intrusa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComSenha(SENHA)))
                .andExpect(status().isForbidden());

        assertThat(clinicas.existsById(clinica.getId())).isTrue();
    }

    @Test
    void umProfissionalNaoPodeExcluirAClinica() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);

        mvc.perform(post("/api/clinicas/{id}/exclusao", clinica.getId())
                        .header(AUTHORIZATION, tokenDe(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComSenha(SENHA)))
                .andExpect(status().isForbidden());

        assertThat(clinicas.existsById(clinica.getId())).isTrue();
    }

    // ------------------------------------------------------------------
    // O que some e o que fica
    // ------------------------------------------------------------------

    /**
     * O teste que mais importa. A outra clínica é montada primeiro e a
     * fotografia de quantas linhas cada tabela tem é tirada; depois a clínica
     * a excluir é montada e excluída. Toda tabela precisa voltar exatamente
     * à fotografia: nada da excluída sobra, nada da outra some.
     */
    @Test
    void apagaTudoDaClinicaENadaDeOutra() throws Exception {
        clinicaCompleta();
        Map<String, Long> soAOutra = linhasPorTabela();
        Clinica excluida = clinicaCompleta();

        mvc.perform(post("/api/clinicas/{id}/exclusao", excluida.getId())
                        .header(AUTHORIZATION, tokenDe(excluida))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComSenha(SENHA)))
                .andExpect(status().isNoContent());

        assertThat(linhasPorTabela()).isEqualTo(soAOutra);
    }

    /**
     * A trava do teste acima. Ele só enxerga as tabelas que o cenário
     * preenche; se alguém criar uma entidade nova, este teste quebra até que
     * ela seja considerada: se pertence a uma clínica, precisa entrar na
     * exclusão de conta e no {@link #clinicaCompleta()}.
     */
    @Test
    void nenhumaEntidadeNovaFicaDeForaDaExclusao() {
        Set<String> entidades = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(entidades)
                .as("Entidade nova no modelo. Se ela pertence a uma clínica, inclua-a em "
                        + "ExclusaoDeContaService e em clinicaCompleta(); depois, em ENTIDADES_CONHECIDAS.")
                .isEqualTo(new TreeSet<>(ENTIDADES_CONHECIDAS));
    }

    @Test
    void oTokenDaClinicaEDosProfissionaisDelaDeixaDeValer() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        String tokenDaClinica = tokenDe(clinica);
        String tokenDaAna = tokenDe(ana);

        mvc.perform(post("/api/clinicas/{id}/exclusao", clinica.getId())
                        .header(AUTHORIZATION, tokenDaClinica)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComSenha(SENHA)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/estatisticas").header(AUTHORIZATION, tokenDaClinica))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/estatisticas").header(AUTHORIZATION, tokenDaAna))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Fotos
    // ------------------------------------------------------------------

    @Test
    void asFotosDaClinicaDosProfissionaisEDosPacientesSaemDoDisco() throws Exception {
        Clinica clinica = novaClinica();
        clinica.setFoto(fotoNoDisco());
        clinicas.save(clinica);
        Fisioterapeuta ana = novoProfissional(clinica);
        ana.setFoto(fotoNoDisco());
        fisioterapeutas.save(ana);
        Paciente joao = novoPaciente(ana);
        joao.setFoto(fotoNoDisco());
        pacientes.save(joao);

        Clinica outra = novaClinica();
        outra.setFoto(fotoNoDisco());
        clinicas.save(outra);

        mvc.perform(post("/api/clinicas/{id}/exclusao", clinica.getId())
                        .header(AUTHORIZATION, tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComSenha(SENHA)))
                .andExpect(status().isNoContent());

        assertThat(existeNoDisco(clinica.getFoto())).isFalse();
        assertThat(existeNoDisco(ana.getFoto())).isFalse();
        assertThat(existeNoDisco(joao.getFoto())).isFalse();
        assertThat(existeNoDisco(outra.getFoto())).as("foto de outra clínica").isTrue();
    }

    /** Apagar foto é melhor esforço: uma foto que já não está lá não desfaz a exclusão. */
    @Test
    void umaFotoQueJaNaoExisteNaoImpedeAExclusao() throws Exception {
        Clinica clinica = novaClinica();
        clinica.setFoto("/uploads/sumiu.png");
        clinicas.save(clinica);

        mvc.perform(post("/api/clinicas/{id}/exclusao", clinica.getId())
                        .header(AUTHORIZATION, tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComSenha(SENHA)))
                .andExpect(status().isNoContent());

        assertThat(clinicas.existsById(clinica.getId())).isFalse();
    }

    /** Uma URL forjada não pode apagar nada fora da pasta de uploads. */
    @Test
    void umaUrlDeFotoForjadaNaoApagaNadaForaDaPastaDeUploads() throws Exception {
        Path pasta = Path.of(pastaDeUploads).toAbsolutePath();
        Files.createDirectories(pasta);
        Path alvo = pasta.getParent().resolve("nao-apague-" + proximo() + ".txt");
        Files.writeString(alvo, "importante");
        try {
            Clinica clinica = novaClinica();
            clinica.setFoto("/uploads/../" + alvo.getFileName());
            clinicas.save(clinica);

            mvc.perform(post("/api/clinicas/{id}/exclusao", clinica.getId())
                            .header(AUTHORIZATION, tokenDe(clinica))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoComSenha(SENHA)))
                    .andExpect(status().isNoContent());

            assertThat(Files.exists(alvo)).isTrue();
        } finally {
            Files.deleteIfExists(alvo);
        }
    }
}
