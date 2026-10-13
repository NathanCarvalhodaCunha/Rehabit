package com.rehabit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Teto para o corpo das requisições que não são envio de arquivo (estas têm
 * o limite de 5 MB do multipart). O maior JSON legítimo do sistema — a
 * anamnese inteira de um paciente — fica bem abaixo de 256 KB; sem teto, um
 * corpo de centenas de megabytes era lido para a memória antes de qualquer
 * validação. O que passar do teto declarado no Content-Length sai com 413 sem
 * ser lido; um corpo sem tamanho declarado (chunked) é cortado ao passar dele.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LimiteDoCorpo extends OncePerRequestFilter {

    static final long LIMITE_BYTES = 256 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (ehEnvioDeFoto(request)) {
            chain.doFilter(request, response);
            return;
        }
        long declarado = request.getContentLengthLong();
        if (declarado > LIMITE_BYTES) {
            // Recusa antes do controller: sem este cabeçalho o site não lê a
            // mensagem (veja JwtAuthenticationFilter.escreverErro).
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"mensagem\":\"Os dados enviados passam do tamanho permitido.\"}");
            return;
        }
        chain.doFilter(declarado < 0 ? new CorpoLimitado(request) : request, response);
    }

    /**
     * As duas rotas que recebem arquivo ficam com o limite do multipart (5 MB).
     * Só elas: dispensar qualquer "Content-Type: multipart/..." dava 5 MB, em
     * vez de 256 KB, a qualquer rota — inclusive as públicas.
     */
    private static boolean ehEnvioDeFoto(HttpServletRequest request) {
        String tipo = request.getContentType();
        String caminho = request.getRequestURI();
        return "POST".equals(request.getMethod())
                && tipo != null && tipo.toLowerCase(Locale.ROOT).startsWith("multipart/")
                && (caminho.equals("/api/uploads") || caminho.equals("/api/auth/cadastro/foto"));
    }

    /** Lê o corpo até o teto e falha depois dele (o Spring transforma isso em 400). */
    private static final class CorpoLimitado extends HttpServletRequestWrapper {

        private ServletInputStream limitado;

        CorpoLimitado(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitado == null) {
                ServletInputStream original = super.getInputStream();
                limitado = new ServletInputStream() {
                    private long lidos;

                    @Override
                    public int read() throws IOException {
                        int b = original.read();
                        if (b >= 0) {
                            contar(1);
                        }
                        return b;
                    }

                    @Override
                    public int read(byte[] destino, int inicio, int quantos) throws IOException {
                        int n = original.read(destino, inicio, quantos);
                        if (n > 0) {
                            contar(n);
                        }
                        return n;
                    }

                    private void contar(int n) throws IOException {
                        lidos += n;
                        if (lidos > LIMITE_BYTES) {
                            throw new IOException("Corpo da requisição acima de " + LIMITE_BYTES + " bytes.");
                        }
                    }

                    @Override
                    public boolean isFinished() {
                        return original.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return original.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        original.setReadListener(listener);
                    }
                };
            }
            return limitado;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String codificacao = getCharacterEncoding();
            Charset charset = codificacao != null ? Charset.forName(codificacao) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
