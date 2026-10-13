package com.rehabit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** Abaixo de 256 bits o HMAC-SHA256 deixa de ser seguro (RFC 7518, 3.2). */
    static final int MINIMO_DE_BYTES = 32;
    /** Tamanho da chave sorteada quando não há uma configurada: dá para o HS512. */
    static final int BYTES_DA_CHAVE_SORTEADA = 64;

    /** Os tipos que a API emite. Um token com qualquer outro é recusado. */
    private static final Set<String> TIPOS = Set.of("CLINICA", "FISIOTERAPEUTA", "DISPOSITIVO");

    private static final String CLAIM_EMITIDO_EM_MS = "emitidoEmMs";

    private final SecretKey chave;
    private final long expiracaoMs;

    public JwtService(@Value("${rehabit.jwt.secret:}") String segredo,
                      @Value("${rehabit.jwt.segredo-obrigatorio:false}") boolean segredoObrigatorio,
                      @Value("${rehabit.jwt.expiracao-ms}") long expiracaoMs) {
        this.chave = Keys.hmacShaKeyFor(bytesDaChave(segredo, segredoObrigatorio));
        this.expiracaoMs = expiracaoMs;
    }

    /**
     * Antes havia um valor-padrão escrito no application.properties: sem a
     * variável, a API subia assinando com uma chave que qualquer leitor do
     * repositório conhecia, e forjava token de qualquer conta. Agora, em
     * produção, falta de chave é erro de subida; fora dela, uma chave sorteada
     * só desta execução deixa o projeto rodar sem configuração nenhuma.
     */
    static byte[] bytesDaChave(String segredo, boolean obrigatorio) {
        if (segredo == null || segredo.isBlank()) {
            if (obrigatorio) {
                throw new IllegalStateException("REHABIT_JWT_SECRET não está definida. Em produção ela é "
                        + "obrigatória: é a chave que assina todos os tokens. Gere uma com "
                        + "\"openssl rand -base64 64\" e defina no ambiente (no Render: Environment).");
            }
            log.warn("REHABIT_JWT_SECRET não está definida: usando uma chave sorteada só para esta execução. "
                    + "Ao reiniciar a API, todos precisam entrar de novo e os goniômetros precisam ser "
                    + "pareados outra vez. Para fixar a chave, veja \"Chave dos tokens\" no README.");
            byte[] sorteada = new byte[BYTES_DA_CHAVE_SORTEADA];
            new SecureRandom().nextBytes(sorteada);
            return sorteada;
        }
        // Sem trim: a chave vale byte a byte, como sempre valeu. Cortar
        // espaços agora invalidaria os tokens de quem já a tinha definido.
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMO_DE_BYTES) {
            throw new IllegalStateException("REHABIT_JWT_SECRET tem " + bytes.length + " bytes; o mínimo é "
                    + MINIMO_DE_BYTES + ". Gere uma com \"openssl rand -base64 64\".");
        }
        return bytes;
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
                .claim(CLAIM_EMITIDO_EM_MS, agora.getTime())
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
            String tipo = claims.get("tipo", String.class);
            if (!TIPOS.contains(tipo)) {
                return null;
            }
            Integer id = Integer.valueOf(claims.getSubject());
            Integer idClinica = claims.get("idClinica", Integer.class);
            return new TokenDados(id, tipo, idClinica, emitidoEm(claims));
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * O iat do JWT guarda só segundos. Comparado com o momento da troca de
     * senha, o token pedido logo depois dela, no mesmo segundo, pareceria
     * anterior — e quem acabou de trocar a senha cairia. Por isso o token
     * leva também o instante em milissegundos. Os emitidos antes deste campo
     * existir ficam com o iat.
     */
    private static Instant emitidoEm(Claims claims) {
        Long emitidoEmMs = claims.get(CLAIM_EMITIDO_EM_MS, Long.class);
        if (emitidoEmMs != null) {
            return Instant.ofEpochMilli(emitidoEmMs);
        }
        Date iat = claims.getIssuedAt();
        return iat == null ? Instant.EPOCH : iat.toInstant();
    }

    /** {@code idClinica} só vem preenchido em token de dispositivo. */
    public record TokenDados(Integer id, String tipo, Integer idClinica, Instant emitidoEm) {
    }
}
