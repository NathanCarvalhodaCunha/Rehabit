package com.rehabit.security;

import com.rehabit.exception.AuthException;
import org.springframework.http.HttpStatus;

public final class PosseChecker {

    private static final String MENSAGEM = "Você não tem acesso a este recurso.";

    private PosseChecker() {
    }

    public static void exigirClinicaDona(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        if (!"CLINICA".equals(usuarioTipo) || !idClinica.equals(usuarioId)) {
            throw new AuthException(MENSAGEM, HttpStatus.FORBIDDEN);
        }
    }

    public static void exigirFisioterapeutaDono(Integer idFisioterapeuta, Integer usuarioId, String usuarioTipo) {
        if (!"FISIOTERAPEUTA".equals(usuarioTipo) || !idFisioterapeuta.equals(usuarioId)) {
            throw new AuthException(MENSAGEM, HttpStatus.FORBIDDEN);
        }
    }

    /** Autorizado se for o fisioterapeuta dono OU a clínica a que ele pertence. */
    public static void exigirDonoOuClinicaDona(Integer idFisioterapeutaDono, Integer idClinicaDona,
                                                Integer usuarioId, String usuarioTipo) {
        boolean ehOFisioterapeutaDono = "FISIOTERAPEUTA".equals(usuarioTipo) && idFisioterapeutaDono.equals(usuarioId);
        boolean ehAClinicaDona = "CLINICA".equals(usuarioTipo) && idClinicaDona.equals(usuarioId);
        if (!ehOFisioterapeutaDono && !ehAClinicaDona) {
            throw new AuthException(MENSAGEM, HttpStatus.FORBIDDEN);
        }
    }
}
