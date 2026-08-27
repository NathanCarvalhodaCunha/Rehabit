package com.rehabit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // Preflight de CORS nunca carrega o header Authorization real — se
        // barrarmos aqui, todo fetch com header customizado quebra antes
        // mesmo de mandar a requisição de verdade.
        if ("OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isPublico(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        JwtService.TokenDados dados = token != null ? jwtService.validar(token) : null;

        if (dados == null) {
            escreverNaoAutenticado(response);
            return;
        }

        request.setAttribute(AuthContext.ATRIBUTO_ID, dados.id());
        request.setAttribute(AuthContext.ATRIBUTO_TIPO, dados.tipo());
        if (dados.idClinica() != null) {
            request.setAttribute(AuthContext.ATRIBUTO_ID_CLINICA, dados.idClinica());
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Compara o path bruto da requisição contra os caminhos públicos. Isso é
     * seguro porque o Spring Security usa StrictHttpFirewall por padrão
     * (via FilterChainProxy), que rejeita URIs não-canônicas (com "..",
     * double-encoding etc.) com 400 antes de qualquer filtro da cadeia
     * rodar — sem essa garantia, um path como "/api/auth/../clinicas/5"
     * poderia escapar desse startsWith().
     */
    private boolean isPublico(HttpServletRequest request) {
        String path = request.getRequestURI();
        String metodo = request.getMethod();
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if (path.startsWith("/uploads/") && "GET".equals(metodo)) {
            return true;
        }
        // O goniômetro ainda não tem token quando vai parear — é justamente o
        // que ele busca aqui. Protegido pelo código de uso único e curta
        // validade, não por autenticação.
        if (path.equals("/api/dispositivos/parear") && "POST".equals(metodo)) {
            return true;
        }
        return path.equals("/api/uploads") && "POST".equals(metodo);
    }

    private void escreverNaoAutenticado(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, String> corpo = new HashMap<>();
        corpo.put("mensagem", "Sessão inválida ou expirada. Faça login novamente.");
        response.getWriter().write(objectMapper.writeValueAsString(corpo));
    }
}
