package com.rehabit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehabit.model.Paciente;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.repository.ArquivoEnviadoRepository;
import com.rehabit.storage.LimpezaDeEnvios;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Foto enviada e nunca usada ficava para sempre no armazenamento — e qualquer
 * conta logada podia enchê-lo. Depois de um dia, a limpeza apaga o que nenhum
 * cadastro passou a usar, e deixa o que está em uso.
 */
class LimpezaDeEnviosTest extends TesteDeIntegracao {

    @Autowired private LimpezaDeEnvios limpeza;
    @Autowired private ArquivoEnviadoRepository enviados;

    @Value("${app.upload-dir}")
    private String pastaDeUploads;

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void limparPasta() {
        FileSystemUtils.deleteRecursively(Path.of(pastaDeUploads).toFile());
    }

    private String enviar(String token) throws Exception {
        String corpo = mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("arquivo", "f.png", "image/png", Imagens.png(2, 2)))
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("url").asText();
    }

    private boolean existe(String url) {
        return Files.exists(Path.of(pastaDeUploads).toAbsolutePath().resolve(url.substring("/uploads/".length())));
    }

    @Test
    void apagaSoOQueNinguemUsou() throws Exception {
        Fisioterapeuta profissional = novoProfissional(novaClinica());
        Paciente paciente = novoPaciente(profissional);
        String esquecida = enviar(tokenDe(profissional));
        String usada = enviar(tokenDe(profissional));
        mvc.perform(put("/api/pacientes/{id}", paciente.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("nome", paciente.getNome(), "foto", usada))))
                .andExpect(status().isOk());

        // Antes de vencer o dia de folga, nada sai.
        assertThat(limpeza.limparEnviadosAntesDe(LocalDateTime.now().minusDays(1))).isZero();
        assertThat(existe(esquecida)).isTrue();

        assertThat(limpeza.limparEnviadosAntesDe(LocalDateTime.now().plusMinutes(1))).isEqualTo(1);
        assertThat(existe(esquecida)).isFalse();
        assertThat(existe(usada)).isTrue();
        assertThat(enviados.count()).isZero();
    }
}
