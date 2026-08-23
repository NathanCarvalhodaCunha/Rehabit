package com.rehabit.security;

import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {

    public static final String ATRIBUTO_ID = "rehabit.authId";
    public static final String ATRIBUTO_TIPO = "rehabit.authTipo";

    private AuthContext() {
    }

    public static Integer id(HttpServletRequest request) {
        return (Integer) request.getAttribute(ATRIBUTO_ID);
    }

    public static String tipo(HttpServletRequest request) {
        return (String) request.getAttribute(ATRIBUTO_TIPO);
    }
}
