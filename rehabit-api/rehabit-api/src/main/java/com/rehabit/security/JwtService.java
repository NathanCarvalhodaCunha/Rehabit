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
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + expiracaoMs);
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("tipo", tipo)
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(chave)
                .compact();
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
            return new TokenDados(id, tipo);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public record TokenDados(Integer id, String tipo) {
    }
}
