package com.rehabit.security;

import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {

    public static final String ATRIBUTO_ID = "rehabit.authId";
    public static final String ATRIBUTO_TIPO = "rehabit.authTipo";
    public static final String ATRIBUTO_ID_CLINICA = "rehabit.authIdClinica";

    /** Só existe em requisição feita por um goniômetro pareado. */
    public static Integer idClinicaDoDispositivo(HttpServletRequest request) {
        return (Integer) request.getAttribute(ATRIBUTO_ID_CLINICA);
    }

    private AuthContext() {
    }

    public static Integer id(HttpServletRequest request) {
        return (Integer) request.getAttribute(ATRIBUTO_ID);
    }

    public static String tipo(HttpServletRequest request) {
        return (String) request.getAttribute(ATRIBUTO_TIPO);
    }
}
