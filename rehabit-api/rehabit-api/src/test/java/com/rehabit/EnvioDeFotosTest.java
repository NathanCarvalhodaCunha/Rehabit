package com.rehabit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehabit.model.Clinica;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.FileSystemUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O /api/uploads era público, confiava no Content-Type e no nome que o
 * navegador manda, e gravava o arquivo com a extensão do cliente: um HTML
 * declarado como image/png virava /uploads/x.html, servido pela API como
 * página. Agora exige login, decide pelo conteúdo, grava com a extensão do
 * tipo de verdade, tira os metadados e serve com cabeçalhos de imagem.
 */
class EnvioDeFotosTest extends TesteDeIntegracao {

    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Value("${app.upload-dir}")
    private String pastaDeUploads;

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void limparPasta() {
        FileSystemUtils.deleteRecursively(Path.of(pastaDeUploads).toFile());
    }

    private ResultActions enviar(String token, String nome, String tipo, byte[] conteudo) throws Exception {
        var pedido = multipart("/api/uploads").file(new MockMultipartFile("arquivo", nome, tipo, conteudo));
        if (token != null) {
            pedido.header("Authorization", token);
        }
        return mvc.perform(pedido);
    }

    private String urlDe(ResultActions resposta) throws Exception {
        return json.readTree(resposta.andReturn().getResponse().getContentAsString()).get("url").asText();
    }

    private byte[] noDisco(String url) throws IOException {
        return Files.readAllBytes(Path.of(pastaDeUploads).toAbsolutePath().resolve(url.substring("/uploads/".length())));
    }

    @Test
    void semLoginNaoEnviaNada() throws Exception {
        enviar(null, "foto.png", "image/png", Imagens.png(4, 3)).andExpect(status().isUnauthorized());
    }

    @Test
    void comLoginAFotoEGravadaComAExtensaoDoTipoDeVerdade() throws Exception {
        Clinica clinica = novaClinica();
        // Nome com caminho e extensão de página: nada disso chega ao disco.
        String url = urlDe(enviar(tokenDe(clinica), "..\\..\\pagina.html", "text/html", Imagens.png(4, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", matchesPattern("/uploads/" + UUID + "\\.png"))));

        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")));
    }

    @Test
    void htmlDeclaradoComoImagemERecusado() throws Exception {
        Clinica clinica = novaClinica();
        byte[] pagina = "<html><script>alert(document.domain)</script></html>".getBytes(StandardCharsets.UTF_8);
        enviar(tokenDe(clinica), "foto.png", "image/png", pagina)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("JPG, PNG")));
    }

    @Test
    void svgERecusadoMesmoSendoImagem() throws Exception {
        Clinica clinica = novaClinica();
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8);
        enviar(tokenDe(clinica), "foto.svg", "image/svg+xml", svg).andExpect(status().isBadRequest());
    }

    @Test
    void acimaDoLimiteResponde413EmVezDe500() throws Exception {
        Clinica clinica = novaClinica();
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(Imagens.png(4, 3), 0, grande, 0, 8);
        enviar(tokenDe(clinica), "grande.png", "image/png", grande)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.mensagem", containsString("5 MB")));
    }

    @Test
    void oJpegDoCelularPerdeGpsComentarioEApendiceMasMantemAOrientacao() throws Exception {
        Clinica clinica = novaClinica();
        byte[] original = Imagens.jpegDeCelular(8, 6);
        assertThat(Imagens.contem(original, Imagens.SEGREDO)).isTrue();

        String url = urlDe(enviar(tokenDe(clinica), "IMG_0001.JPG", "image/jpeg", original)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", matchesPattern("/uploads/" + UUID + "\\.jpg"))));
        byte[] salvo = noDisco(url);

        assertThat(Imagens.contem(salvo, Imagens.SEGREDO)).as("GPS, comentário ou apêndice sobrou").isFalse();
        // Só a orientação ficou: EXIF big-endian mínimo com 0x0112 = 6.
        assertThat(Imagens.contem(salvo, "Exif\0\0MM\0*")).isTrue();
        assertThat(Imagens.contem(salvo, new String(new byte[] {0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06},
                StandardCharsets.ISO_8859_1))).isTrue();
        // E a imagem continua abrindo, do mesmo tamanho.
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(salvo));
        assertThat(lida).isNotNull();
        assertThat(lida.getWidth()).isEqualTo(8);
        assertThat(lida.getHeight()).isEqualTo(6);
    }

    @Test
    void oPngPerdeOsTextos() throws Exception {
        Clinica clinica = novaClinica();
        byte[] original = Imagens.pngComTexto(5, 5);
        assertThat(Imagens.contem(original, Imagens.SEGREDO)).isTrue();

        byte[] salvo = noDisco(urlDe(enviar(tokenDe(clinica), "a.png", "image/png", original)
                .andExpect(status().isCreated())));

        assertThat(Imagens.contem(salvo, Imagens.SEGREDO)).isFalse();
        assertThat(ImageIO.read(new ByteArrayInputStream(salvo)).getWidth()).isEqualTo(5);
    }

    @Test
    void arquivoAntigoQueNaoEImagemNaoEServido() throws Exception {
        // Como ficaria a pasta depois do upload antigo, que gravava a extensão do cliente.
        Path pasta = Path.of(pastaDeUploads).toAbsolutePath();
        Files.createDirectories(pasta);
        Files.writeString(pasta.resolve("antigo.html"), "<script>alert(1)</script>");
        Files.writeString(pasta.resolve("antigo.svg"), "<svg onload=\"alert(1)\"/>");

        mvc.perform(get("/uploads/antigo.html")).andExpect(status().isNotFound());
        mvc.perform(get("/uploads/antigo.svg")).andExpect(status().isNotFound());
    }
}
