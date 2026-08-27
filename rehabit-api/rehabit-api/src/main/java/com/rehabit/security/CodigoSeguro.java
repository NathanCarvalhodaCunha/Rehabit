package com.rehabit.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Geração e conferência dos segredos de uso único (token do link de
 * recuperação e códigos de 6 dígitos).
 *
 * O banco guarda só o hash: um vazamento da tabela não entrega a conta de
 * ninguém. A comparação é feita em tempo constante para não abrir margem a
 * adivinhar o código medindo quanto tempo a resposta demora.
 */
public final class CodigoSeguro {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CodigoSeguro() {
    }

    /** Código numérico para digitar (ex.: 6 dígitos), com zeros à esquerda quando cair. */
    public static String gerarCodigoNumerico(int digitos) {
        StringBuilder codigo = new StringBuilder(digitos);
        for (int i = 0; i < digitos; i++) {
            codigo.append(ALEATORIO.nextInt(10));
        }
        return codigo.toString();
    }

    /** Token longo para o link do e-mail: 32 bytes aleatórios em Base64 seguro para URL. */
    public static String gerarToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String valor) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 é obrigatório em qualquer JVM; se sumir, não há como seguir.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", ex);
        }
    }

    public static boolean confere(String valorInformado, String hashGuardado) {
        if (valorInformado == null || hashGuardado == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(valorInformado).getBytes(StandardCharsets.UTF_8),
                hashGuardado.getBytes(StandardCharsets.UTF_8));
    }
}
