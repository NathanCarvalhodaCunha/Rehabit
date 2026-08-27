package com.rehabit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey chave;
    private final long expiracaoMs;

    public JwtService(@Value("${rehabit.jwt.secret}") String segredo,
                       @Value("${rehabit.jwt.expiracao-ms}") long expiracaoMs) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMs = expiracaoMs;
    }

    public String gerarToken(Integer id, String tipo) {
        return gerarToken(id, tipo, expiracaoMs, null);
    }

    /**
     * Variante para o goniômetro: o aparelho fica ligado indefinidamente e não
     * tem como alguém refazer login nele, então o token dura muito mais. Quem
     * corta o acesso é a flag "ativo" do dispositivo, conferida a cada leitura.
     */
    public String gerarTokenDeDispositivo(Integer idDispositivo, Integer idClinica, long validadeMs) {
        return gerarToken(idDispositivo, "DISPOSITIVO", validadeMs, idClinica);
    }

    private String gerarToken(Integer id, String tipo, long validadeMs, Integer idClinica) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + validadeMs);
        var builder = Jwts.builder()
                .subject(String.valueOf(id))
                .claim("tipo", tipo)
                .issuedAt(agora)
                .expiration(expiracao);
        if (idClinica != null) {
            builder.claim("idClinica", idClinica);
        }
        return builder.signWith(chave).compact();
    }

    /**
     * Nunca lança — token ausente/expirado/inválido/adulterado tudo vira
     * null, pra o filtro tratar todo caso de falha do mesmo jeito (401).
     */
    public TokenDados validar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Integer id = Integer.valueOf(claims.getSubject());
            String tipo = claims.get("tipo", String.class);
            Integer idClinica = claims.get("idClinica", Integer.class);
            return new TokenDados(id, tipo, idClinica);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    /** {@code idClinica} só vem preenchido em token de dispositivo. */
    public record TokenDados(Integer id, String tipo, Integer idClinica) {
    }
}
