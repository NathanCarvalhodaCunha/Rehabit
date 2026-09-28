package com.rehabit.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Só a leitura do public_id a partir da URL: a chamada ao Cloudinary em si
 * precisa de credencial e fica fora dos testes.
 */
class CloudinaryFileStorageServiceTest {

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
