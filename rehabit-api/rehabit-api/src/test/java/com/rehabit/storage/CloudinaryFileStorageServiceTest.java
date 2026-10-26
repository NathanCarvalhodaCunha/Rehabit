package com.rehabit.storage;

import com.cloudinary.Cloudinary;
import com.rehabit.exception.AuthException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A leitura do public_id a partir da URL e o envio em si, contra um servidor
 * falso no lugar do Cloudinary (o upload_prefix da CLOUDINARY_URL aponta para
 * ele): a conta de verdade precisa de credencial e fica fora dos testes.
 */
class CloudinaryFileStorageServiceTest {

    private static final byte[] FOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private HttpServer servidor;

    @AfterEach
    void desligarServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    /** O Cloudinary falso responde todo upload com este código e corpo. */
    private CloudinaryFileStorageService cloudinaryQueResponde(int codigo, String corpo) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1_1/rehabit/image/upload", troca -> {
            troca.getRequestBody().readAllBytes();
            byte[] resposta = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(codigo, resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.start();
        return cloudinaryEm("http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    private static CloudinaryFileStorageService cloudinaryEm(String endereco) {
        return new CloudinaryFileStorageService(
                new Cloudinary("cloudinary://123456:segredo@rehabit?upload_prefix=" + endereco));
    }

    private static String erro(String mensagem) {
        return "{\"error\":{\"message\":\"" + mensagem + "\"}}";
    }

    @Test
    void envioAceitoDevolveASecureUrl() throws IOException {
        String url = "https://res.cloudinary.com/rehabit/image/upload/v1712345678/abc123xyz.jpg";
        CloudinaryFileStorageService armazenamento = cloudinaryQueResponde(200,
                "{\"public_id\":\"abc123xyz\",\"format\":\"jpg\",\"secure_url\":\"" + url + "\"}");

        assertThat(armazenamento.salvar(FOTO, TipoDeImagem.JPEG)).isEqualTo(url);
        assertThat(armazenamento.guardou(url)).isTrue();
    }

    /**
     * Antes o SDK lançava RuntimeException com o texto do Cloudinary, e a
     * troca de foto respondia "Erro interno no servidor" (500).
     */
    @Test
    void imagemRecusadaPeloCloudinaryVolta400ComOMotivo() throws IOException {
        CloudinaryFileStorageService armazenamento = cloudinaryQueResponde(400, erro("Invalid image file"));

        assertThatThrownBy(() -> armazenamento.salvar(FOTO, TipoDeImagem.JPEG))
                .isInstanceOfSatisfying(AuthException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Não foi possível usar esta imagem");
    }

    /** Um logo exportado em alta resolução passa do limite do plano gratuito pesando pouco. */
    @Test
    void imagemAcimaDosMegapixelsDoPlanoVolta400() throws IOException {
        CloudinaryFileStorageService armazenamento = cloudinaryQueResponde(400,
                erro("Maximum image size is 25 Megapixels. Requested 64.00 Megapixels"));

        assertThatThrownBy(() -> armazenamento.salvar(FOTO, TipoDeImagem.PNG))
                .isInstanceOfSatisfying(AuthException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("resolução alta demais");
    }

    /** Credencial errada na CLOUDINARY_URL não é culpa da foto: 503, não 400 nem 500. */
    @Test
    void credencialRecusadaVolta503() throws IOException {
        CloudinaryFileStorageService armazenamento = cloudinaryQueResponde(401,
                erro("Invalid Signature 0123abc. String to sign - 'allowed_formats=jpg,png,gif,webp&timestamp=1'."));

        assertThatThrownBy(() -> armazenamento.salvar(FOTO, TipoDeImagem.JPEG))
                .isInstanceOfSatisfying(AuthException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessageContaining("indisponível");
    }

    @Test
    void limiteDeEnviosDoCloudinaryVolta503() throws IOException {
        CloudinaryFileStorageService armazenamento = cloudinaryQueResponde(420, erro("Rate Limit Exceeded"));

        assertThatThrownBy(() -> armazenamento.salvar(FOTO, TipoDeImagem.JPEG))
                .isInstanceOfSatisfying(AuthException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessageContaining("Tente de novo em alguns minutos");
    }

    @Test
    void cloudinaryForaDoArVolta503() throws IOException {
        CloudinaryFileStorageService armazenamento = cloudinaryQueResponde(200, "{}");
        servidor.stop(0);
        servidor = null;

        assertThatThrownBy(() -> armazenamento.salvar(FOTO, TipoDeImagem.JPEG))
                .isInstanceOfSatisfying(AuthException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void respostaSemUrlVolta503() throws IOException {
        CloudinaryFileStorageService armazenamento = cloudinaryQueResponde(200, "{\"public_id\":\"abc123xyz\"}");

        assertThatThrownBy(() -> armazenamento.salvar(FOTO, TipoDeImagem.JPEG))
                .isInstanceOfSatisfying(AuthException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void tiraOPublicIdDaUrlQueOUploadDevolve() {
        assertThat(CloudinaryFileStorageService.publicIdDe(
                "https://res.cloudinary.com/rehabit/image/upload/v1712345678/abc123xyz.jpg"))
                .isEqualTo("abc123xyz");
    }

    @Test
    void urlDeFotoLocalNaoEDoCloudinary() {
        assertThat(CloudinaryFileStorageService.publicIdDe("/uploads/abc123xyz.png")).isNull();
    }

    @Test
    void urlDeOutroSiteNaoEDoCloudinary() {
        assertThat(CloudinaryFileStorageService.publicIdDe("https://exemplo.com/image/upload/v1/abc.jpg")).isNull();
    }

    @Test
    void semFotoNaoHaOQueApagar() {
        assertThat(CloudinaryFileStorageService.publicIdDe(null)).isNull();
    }
}
