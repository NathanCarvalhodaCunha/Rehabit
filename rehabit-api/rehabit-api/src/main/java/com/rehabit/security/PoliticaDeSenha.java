package com.rehabit.security;

import com.rehabit.exception.AuthException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regras para senha nova: cadastro, profissional criado pela clínica, troca,
 * redefinição por e-mail e edição de perfil. O login não passa por aqui —
 * quem já tem conta com senha antiga, mais curta, continua entrando.
 *
 * Os DTOs usam a anotação {@link SenhaForte}; os services que conferem a
 * senha à mão (edição de perfil) chamam {@link #exigir}.
 */
public final class PoliticaDeSenha {

    private static final Logger log = LoggerFactory.getLogger(PoliticaDeSenha.class);

    public static final int MINIMO_DE_CARACTERES = 8;
    /**
     * O BCrypt só lê os primeiros 72 bytes: o resto era ignorado sem aviso, e
     * o Spring Security atual recusa cifrar senha maior (viraria erro 500).
     */
    public static final int MAXIMO_DE_BYTES = 72;

    private static final Set<String> COMUNS = carregarComuns();

    private PoliticaDeSenha() {
    }

    /** O motivo para recusar a senha, ou null quando ela serve. */
    public static String problema(String senha) {
        if (senha == null || senha.codePointCount(0, senha.length()) < MINIMO_DE_CARACTERES) {
            return "A senha deve ter ao menos " + MINIMO_DE_CARACTERES + " caracteres.";
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_DE_BYTES) {
            return "A senha é longa demais: use até " + MAXIMO_DE_BYTES
                    + " caracteres (letras acentuadas contam como dois).";
        }
        if (COMUNS.contains(senha.toLowerCase(Locale.ROOT))) {
            return "Essa senha é muito comum e fácil de adivinhar. Escolha outra.";
        }
        return null;
    }

    /** @throws AuthException 400 com o motivo, quando a senha não serve */
    public static void exigir(String senha) {
        String problema = problema(senha);
        if (problema != null) {
            throw new AuthException(problema, HttpStatus.BAD_REQUEST);
        }
    }

    private static Set<String> carregarComuns() {
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(
                new ClassPathResource("senhas-comuns.txt").getInputStream(), StandardCharsets.UTF_8))) {
            return leitor.lines()
                    .map(String::trim)
                    .map(linha -> linha.toLowerCase(Locale.ROOT))
                    .filter(linha -> !linha.isEmpty() && !linha.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException ex) {
            log.error("Não foi possível ler a lista de senhas comuns; valerão só os limites de tamanho.", ex);
            return Set.of();
        }
    }

    /** Validador da anotação {@link SenhaForte}, com a mensagem do motivo de cada recusa. */
    public static class Validador implements ConstraintValidator<SenhaForte, String> {

        @Override
        public boolean isValid(String senha, ConstraintValidatorContext contexto) {
            // Ausente é com o @NotBlank do campo, que tem a mensagem própria.
            if (senha == null) {
                return true;
            }
            String problema = problema(senha);
            if (problema == null) {
                return true;
            }
            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(problema).addConstraintViolation();
            return false;
        }
    }
}
