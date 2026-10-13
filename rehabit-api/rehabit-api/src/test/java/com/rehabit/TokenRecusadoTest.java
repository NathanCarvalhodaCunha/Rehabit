package com.rehabit;

import com.rehabit.model.Clinica;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Token que não foi a API que emitiu, ou de um tipo que ela não emite, é 401. */
class TokenRecusadoTest extends TesteDeIntegracao {

    @Value("${rehabit.jwt.secret}")
    private String chaveDosTestes;

    private static String assinado(String chave, String sujeito, String tipo) {
        Date agora = new Date();
        return Jwts.builder()
                .subject(sujeito)
                .claim("tipo", tipo)
                .issuedAt(agora)
                .expiration(new Date(agora.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(chave.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void tokenAssinadoComOutraChaveRecebe401() throws Exception {
        Clinica clinica = novaClinica();
        String forjado = assinado("uma-chave-qualquer-que-nao-e-a-da-api-0123456789",
                String.valueOf(clinica.getId()), "CLINICA");

        mvc.perform(get("/api/clinicas/{id}", clinica.getId()).header("Authorization", "Bearer " + forjado))
                .andExpect(status().isUnauthorized());
    }

    /** As configurações devolviam os padrões (200) para qualquer tipo que viesse no token. */
    @Test
    void tokenComTipoQueAApiNaoEmiteRecebe401() throws Exception {
        Clinica clinica = novaClinica();
        String tipoInventado = assinado(chaveDosTestes, String.valueOf(clinica.getId()), "ADMIN");

        mvc.perform(get("/api/configuracoes").header("Authorization", "Bearer " + tipoInventado))
                .andExpect(status().isUnauthorized());
    }
}
