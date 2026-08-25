package com.rehabit.config;

import com.rehabit.storage.CloudinaryFileStorageService;
import com.rehabit.storage.FileStorageService;
import com.rehabit.storage.LocalFileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStorageConfig {

    /**
     * Usa Cloudinary sempre que CLOUDINARY_URL estiver configurada (fotos
     * persistem entre deploys); caso contrário cai para o disco local
     * (uploads/), suficiente para desenvolvimento mas efêmero em serviços
     * sem disco persistente. A escolha não depende de nenhum profile do
     * Spring — só da variável de ambiente realmente estar presente.
     */
    @Bean
    public FileStorageService fileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        String cloudinaryUrl = System.getenv("CLOUDINARY_URL");
        if (cloudinaryUrl != null && !cloudinaryUrl.isBlank()) {
            return new CloudinaryFileStorageService();
        }
        return new LocalFileStorageService(uploadDir);
    }
}
