package com.rehabit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O campo "foto" aceitava qualquer texto, e as telas o punham num
 * style="background-image:url(...)": com aspas no meio, o valor fechava o
 * atributo e virava HTML — script na sessão de quem abrisse a lista de
 * profissionais (a clínica inclusive). Agora só entra URL que o próprio
 * armazenamento devolveu. E arquivo só é apagado quando ninguém mais o usa.
 */
class CampoFotoTest extends TesteDeIntegracao {

    /** Valores que já foram aceitos e gravados. */
    private static final List<String> FOTOS_HOSTIS = List.of(
            "x\" onerror=\"alert(1)",
            "/uploads/a.png\");background:url(javascript:alert(1)",
            "javascript:alert(document.cookie)",
            "https://atacante.example/rastreador.png",
            "/uploads/../application.properties",
            "/uploads/00000000-0000-0000-0000-000000000000.png");   // formato certo, arquivo que não existe

    @Value("${app.upload-dir}")
    private String pastaDeUploads;

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void limparPasta() {
        FileSystemUtils.deleteRecursively(Path.of(pastaDeUploads).toFile());
    }

    private String enviarFoto(String token) throws Exception {
        String corpo = mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("arquivo", "f.png", "image/png", Imagens.png(3, 3)))
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("url").asText();
    }

    private boolean existe(String url) {
        return Files.exists(Path.of(pastaDeUploads).toAbsolutePath().resolve(url.substring("/uploads/".length())));
    }

    private String corpoDoProfissional(Fisioterapeuta f, String foto) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("nome", f.getNome());
        corpo.put("email", f.getEmail());
        corpo.put("foto", foto);
        return json.writeValueAsString(corpo);
    }

    private String corpoDaClinica(Clinica c, String foto) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("nome", c.getNome());
        corpo.put("cnpj", c.getCnpj());
        corpo.put("email", c.getEmail());
        corpo.put("foto", foto);
        return json.writeValueAsString(corpo);
    }

    private String corpoDoPaciente(Paciente p, String foto) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("nome", p.getNome());
        corpo.put("foto", foto);
        return json.writeValueAsString(corpo);
    }

    @Test
    void fotoQueNaoVeioDoUploadERecusadaNoPerfilDoProfissional() throws Exception {
        Fisioterapeuta profissional = novoProfissional(novaClinica());

        for (String hostil : FOTOS_HOSTIS) {
            mvc.perform(put("/api/fisioterapeutas/{id}", profissional.getId())
                            .header("Authorization", tokenDe(profissional))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoDoProfissional(profissional, hostil)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem", containsString("Foto inválida")));
        }
        assertThat(fisioterapeutas.findById(profissional.getId()).orElseThrow().getFoto()).isNull();

        String daApi = enviarFoto(tokenDe(profissional));
        mvc.perform(put("/api/fisioterapeutas/{id}", profissional.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoProfissional(profissional, daApi)))
                .andExpect(status().isOk());
        assertThat(fisioterapeutas.findById(profissional.getId()).orElseThrow().getFoto()).isEqualTo(daApi);
    }

    @Test
    void fotoHostilERecusadaNoCadastroDaClinicaNoDoProfissionalENoDoPaciente() throws Exception {
        String hostil = FOTOS_HOSTIS.get(0);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "tipo", "instituicao", "nome", "Clínica Nova", "email", "nova" + proximo() + "@teste.rehabit",
                                "senha", "Senha-Do-Cadastro-1", "cnpj", String.format("%014d", 90_000 + proximo()),
                                "foto", hostil))))
                .andExpect(status().isBadRequest());

        Clinica clinica = novaClinica();
        int n = proximo();
        mvc.perform(post("/api/fisioterapeutas")
                        .header("Authorization", tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Prof " + n, "email", "prof" + n + "@teste.rehabit", "coffito", "C-" + n,
                                "senha", "Senha-Do-Cadastro-1", "idClinica", clinica.getId(), "foto", hostil))))
                .andExpect(status().isBadRequest());

        Fisioterapeuta profissional = novoProfissional(clinica);
        mvc.perform(post("/api/pacientes")
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Paciente X", "cpf", String.format("%011d", 50_000 + proximo()),
                                "idFisioterapeuta", profissional.getId(), "foto", hostil))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fotoAntigaDeAntesDaRegraContinuaAoSalvarOPerfil() throws Exception {
        // Gravada antes desta validação, com o nome que o upload antigo dava.
        Clinica clinica = novaClinica();
        clinica.setFoto("/uploads/foto-antiga.jpeg");
        clinicas.save(clinica);

        mvc.perform(put("/api/clinicas/{id}", clinica.getId())
                        .header("Authorization", tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaClinica(clinica, "/uploads/foto-antiga.jpeg")))
                .andExpect(status().isOk());
        assertThat(clinicas.findById(clinica.getId()).orElseThrow().getFoto()).isEqualTo("/uploads/foto-antiga.jpeg");
    }

    @Test
    void excluirAContaNaoApagaAFotoDeOutraClinica() throws Exception {
        Clinica vitima = novaClinica();
        String fotoDaVitima = enviarFoto(tokenDe(vitima));
        mvc.perform(put("/api/clinicas/{id}", vitima.getId())
                        .header("Authorization", tokenDe(vitima))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaClinica(vitima, fotoDaVitima)))
                .andExpect(status().isOk());

        // O atacante põe na própria conta a URL da foto da vítima e exclui a conta.
        Clinica atacante = novaClinica();
        mvc.perform(put("/api/clinicas/{id}", atacante.getId())
                        .header("Authorization", tokenDe(atacante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaClinica(atacante, fotoDaVitima)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/clinicas/{id}/exclusao", atacante.getId())
                        .header("Authorization", tokenDe(atacante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(existe(fotoDaVitima)).as("a foto da vítima continua no disco").isTrue();
    }

    @Test
    void trocarAFotoDoPacienteApagaAAnteriorSeNinguemMaisUsa() throws Exception {
        Fisioterapeuta profissional = novoProfissional(novaClinica());
        Paciente paciente = novoPaciente(profissional);
        String primeira = enviarFoto(tokenDe(profissional));
        String segunda = enviarFoto(tokenDe(profissional));

        mvc.perform(put("/api/pacientes/{id}", paciente.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoPaciente(paciente, primeira)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/pacientes/{id}", paciente.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoPaciente(paciente, segunda)))
                .andExpect(status().isOk());

        assertThat(existe(primeira)).as("foto trocada saiu do disco").isFalse();
        assertThat(existe(segunda)).isTrue();
    }

    @Test
    void trocarAFotoNaoApagaArquivoQueOutroCadastroUsa() throws Exception {
        Fisioterapeuta profissional = novoProfissional(novaClinica());
        Paciente um = novoPaciente(profissional);
        Paciente outro = novoPaciente(profissional);
        String compartilhada = enviarFoto(tokenDe(profissional));
        String nova = enviarFoto(tokenDe(profissional));

        for (Paciente p : List.of(um, outro)) {
            mvc.perform(put("/api/pacientes/{id}", p.getId())
                            .header("Authorization", tokenDe(profissional))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoDoPaciente(p, compartilhada)))
                    .andExpect(status().isOk());
        }
        mvc.perform(put("/api/pacientes/{id}", um.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoPaciente(um, nova)))
                .andExpect(status().isOk());

        assertThat(existe(compartilhada)).isTrue();
    }
}
