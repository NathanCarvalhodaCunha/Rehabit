package com.rehabit.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rehabit.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Guarda o arquivo no Cloudinary (armazenamento persistente). Escolhido
 * em FileStorageConfig sempre que a variável de ambiente CLOUDINARY_URL
 * está definida, independentemente do profile do Spring ativo — assim
 * um deploy que esqueça de ativar o profile "cloud" não volta a gravar
 * fotos no disco local (efêmero) por engano.
 */
public class CloudinaryFileStorageService implements FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(CloudinaryFileStorageService.class);

    private final Cloudinary cloudinary;

    public CloudinaryFileStorageService() {
        // O construtor sem argumentos lê a variável de ambiente
        // CLOUDINARY_URL sozinho (formato
        // cloudinary://<api_key>:<api_secret>@<cloud_name>).
        this(new Cloudinary());
    }

    /** Para os testes, que apontam o upload_prefix para um servidor falso. */
    CloudinaryFileStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
        if (cloudinary.config.apiKey == null) {
            throw new IllegalStateException(
                    "Variável de ambiente CLOUDINARY_URL não configurada (formato cloudinary://<api_key>:<api_secret>@<cloud_name>).");
        }
    }

    @Override
    public String salvar(byte[] conteudo, TipoDeImagem tipo) {
        Map<?, ?> resultado;
        try {
            // O Cloudinary confere o formato de novo do lado dele: só imagem,
            // e só os formatos que a API aceita (SVG, por exemplo, não).
            //
            // Com return_error, a recusa do Cloudinary volta no mapa, com o
            // código HTTP. Sem ela, o SDK lança RuntimeException só com o
            // texto em inglês, que caía no GlobalExceptionHandler como "Erro
            // interno no servidor" — era o que a tela mostrava ao trocar a
            // foto, sem dizer se o problema era a imagem ou a conta.
            resultado = cloudinary.uploader().upload(conteudo, ObjectUtils.asMap(
                    "resource_type", "image",
                    "allowed_formats", "jpg,png,gif,webp",
                    "return_error", true));
        } catch (IOException ex) {
            logger.warn("Sem resposta do Cloudinary ao enviar uma foto", ex);
            throw indisponivel();
        } catch (RuntimeException ex) {
            // Configuração quebrada (CLOUDINARY_URL sem a api_secret, por
            // exemplo): o SDK recusa antes de mandar.
            logger.error("Falha ao enviar foto ao Cloudinary; confira a CLOUDINARY_URL", ex);
            throw indisponivel();
        }

        if (resultado.get("error") instanceof Map<?, ?> erro) {
            int codigo = erro.get("http_code") instanceof Number n ? n.intValue() : 0;
            String mensagem = String.valueOf(erro.get("message"));
            if (codigo == 400) {
                logger.warn("Cloudinary recusou a foto ({}): {}", tipo, mensagem);
            } else {
                logger.error("Cloudinary recusou o envio de foto (HTTP {}): {}", codigo, mensagem);
            }
            throw recusa(codigo, mensagem);
        }

        Object url = resultado.get("secure_url");
        if (!(url instanceof String texto) || texto.isBlank()) {
            logger.error("Cloudinary respondeu ao envio de foto sem secure_url: {}", resultado);
            throw indisponivel();
        }
        return texto;
    }

    /**
     * A recusa do Cloudinary em palavras de quem está na tela. 400 é a
     * imagem (corrompida, formato, resolução, tamanho); o resto é o serviço
     * ou a conta (credencial, cota, limite de envios por hora), que a pessoa
     * não resolve trocando a foto.
     */
    static AuthException recusa(int codigo, String mensagem) {
        String texto = mensagem == null ? "" : mensagem.toLowerCase(Locale.ROOT);
        if (codigo == 400) {
            if (texto.contains("megapixel")) {
                // "Maximum image size is 25 Megapixels. Requested 59.84 Megapixels"
                return new AuthException("A imagem tem resolução alta demais. Reduza o tamanho dela em pixels e envie de novo.",
                        HttpStatus.BAD_REQUEST);
            }
            if (texto.contains("file size")) {
                return new AuthException("A foto passa do tamanho permitido. Escolha uma imagem menor.",
                        HttpStatus.BAD_REQUEST);
            }
            if (texto.contains("stale request")) {
                // Relógio do servidor fora de hora: problema daqui, não da foto.
                return indisponivel();
            }
            return new AuthException("Não foi possível usar esta imagem. Envie uma foto em JPG, PNG, GIF ou WEBP.",
                    HttpStatus.BAD_REQUEST);
        }
        if (codigo == 420 || codigo == 429) {
            return new AuthException("Muitas fotos enviadas agora. Tente de novo em alguns minutos.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        return indisponivel();
    }

    private static AuthException indisponivel() {
        return new AuthException("O envio de fotos está indisponível no momento. Tente de novo mais tarde.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public boolean guardou(String url) {
        return urlDaConta(url, cloudinary.config.cloudName);
    }

    /**
     * O formato exato da secure_url que o upload acima devolve, na conta
     * configurada: ".../<conta>/image/upload/v<versão>/<id>.<formato>". Uma
     * URL de outra conta do Cloudinary, ou com transformação, não passa.
     */
    static boolean urlDaConta(String url, String nomeDaConta) {
        if (url == null || nomeDaConta == null || nomeDaConta.isBlank()) {
            return false;
        }
        return Pattern.matches("https://res\\.cloudinary\\.com/" + Pattern.quote(nomeDaConta)
                + "/image/upload/v[0-9]{1,15}/[A-Za-z0-9_-]{1,100}\\.(jpg|png|gif|webp)", url);
    }

    @Override
    public void excluir(String url) {
        String publicId = publicIdDe(url);
        if (publicId == null) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception ex) {
            logger.warn("Falha ao apagar do Cloudinary o arquivo {}", publicId, ex);
        }
    }

    /**
     * O public_id de uma URL devolvida por {@link #salvar}. O upload é feito
     * sem pasta, então a URL termina em ".../upload/v<versão>/<id>.<ext>" e
     * o id é o último trecho sem a extensão. Nulo para o que não é do
     * Cloudinary — uma foto antiga do disco local, por exemplo.
     */
    static String publicIdDe(String url) {
        if (url == null || !url.startsWith("https://res.cloudinary.com/") || !url.contains("/upload/")) {
            return null;
        }
        String ultimoTrecho = url.substring(url.lastIndexOf('/') + 1);
        int ponto = ultimoTrecho.lastIndexOf('.');
        String id = ponto > 0 ? ultimoTrecho.substring(0, ponto) : ultimoTrecho;
        return id.isBlank() ? null : id;
    }
}
