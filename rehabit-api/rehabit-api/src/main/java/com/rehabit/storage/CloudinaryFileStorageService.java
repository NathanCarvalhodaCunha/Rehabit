package com.rehabit.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rehabit.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

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
        this.cloudinary = new Cloudinary();
        if (cloudinary.config.apiKey == null) {
            throw new IllegalStateException(
                    "Variável de ambiente CLOUDINARY_URL não configurada (formato cloudinary://<api_key>:<api_secret>@<cloud_name>).");
        }
    }

    @Override
    public String salvar(MultipartFile arquivo) {
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(arquivo.getBytes(), ObjectUtils.emptyMap());
            String url = (String) resultado.get("secure_url");
            if (url == null) {
                throw new AuthException("Falha ao salvar o arquivo.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return url;
        } catch (IOException ex) {
            logger.warn("Falha ao enviar arquivo para o Cloudinary", ex);
            throw new AuthException("Falha ao salvar o arquivo.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
