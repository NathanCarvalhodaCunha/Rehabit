package com.rehabit.service;

import com.rehabit.exception.AuthException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os tetos de telas abertas no SSE. Antes não havia nenhum: uma conta logada
 * abria quantas conexões quisesse, cada uma presa na memória e no ping de 20 s.
 */
class GoniometroStreamTest {

    @Test
    void aQuartaTelaDaMesmaPessoaDerrubaAMaisAntiga() {
        GoniometroStream stream = new GoniometroStream();
        for (int i = 0; i < 4; i++) {
            stream.inscrever(1, "FISIOTERAPEUTA:7");
        }
        stream.inscrever(1, "CLINICA:1");

        assertThat(stream.abertas(1)).isEqualTo(GoniometroStream.MAXIMO_POR_PESSOA + 1);
    }

    @Test
    void aClinicaTemTetoEPassarDeleE429() {
        GoniometroStream stream = new GoniometroStream();
        for (int i = 0; i < GoniometroStream.MAXIMO_POR_PESSOA; i++) {
            stream.inscrever(2, "CLINICA:2");
        }
        for (int i = GoniometroStream.MAXIMO_POR_PESSOA; i < GoniometroStream.MAXIMO_POR_CLINICA; i++) {
            stream.inscrever(2, "FISIOTERAPEUTA:" + i);
        }

        assertThatThrownBy(() -> stream.inscrever(2, "FISIOTERAPEUTA:999"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(stream.abertas(2)).isEqualTo(GoniometroStream.MAXIMO_POR_CLINICA);
        // Quem já está no teto dele troca a tela mais antiga, mesmo com a clínica cheia.
        stream.inscrever(2, "CLINICA:2");
        assertThat(stream.abertas(2)).isEqualTo(GoniometroStream.MAXIMO_POR_CLINICA);
    }

    @Test
    void oServidorTodoTemTeto() {
        GoniometroStream stream = new GoniometroStream();
        int clinicas = GoniometroStream.MAXIMO_NO_SERVIDOR / GoniometroStream.MAXIMO_POR_CLINICA;
        for (int c = 0; c < clinicas; c++) {
            for (int i = 0; i < GoniometroStream.MAXIMO_POR_CLINICA; i++) {
                stream.inscrever(100 + c, "CLINICA:" + i);
            }
        }
        assertThatThrownBy(() -> stream.inscrever(9999, "CLINICA:9999"))
                .isInstanceOf(AuthException.class);
        assertThat(stream.abertas(9999)).isZero();
    }
}
