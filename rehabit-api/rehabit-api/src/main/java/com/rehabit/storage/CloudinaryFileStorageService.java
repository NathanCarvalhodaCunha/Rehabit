package com.rehabit.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rehabit.exception.AuthException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@Profile("cloud")
public class CloudinaryFileStorageService implements FileStorageService {

    private final Cloudinary cloudinary;

    public CloudinaryFileStorageService() {
        // O construtor sem argumentos lê a variável de ambiente
        // CLOUDINARY_URL sozinho (formato
        // cloudinary://<api_key>:<api_secret>@<cloud_name>).
        this.cloudinary = new Cloudinary();
    }

    @Override
    public String salvar(MultipartFile arquivo) {
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(arquivo.getBytes(), ObjectUtils.emptyMap());
            return (String) resultado.get("secure_url");
        } catch (IOException ex) {
            throw new AuthException("Falha ao salvar o arquivo.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
