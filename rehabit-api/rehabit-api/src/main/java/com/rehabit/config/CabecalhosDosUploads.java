package com.rehabit.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * O que sai de /uploads/** sai como imagem e nada mais.
 *
 * Os envios de hoje já só gravam imagem com a extensão do tipo de verdade
 * (veja EnvioDeFoto), mas a pasta pode ter arquivos de antes disso — um
 * "x.html" enviado como "image/png" era gravado e servido como página, na
 * origem da API. Por isso: extensão fora da lista de imagens dá 404, e o resto
 * vai com uma CSP que proíbe tudo e põe o documento em sandbox (se um navegador
 * abrir o arquivo como página, ele não roda script nem herda a origem) e com
 * nosniff (o navegador não "adivinha" outro tipo pelo conteúdo).
 */
class CabecalhosDosUploads implements HandlerInterceptor {

    private static final Set<String> EXTENSOES_DE_IMAGEM = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!ehImagem(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"mensagem\":\"Recurso não encontrado.\"}");
            return false;
        }
        response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
        response.setHeader("X-Content-Type-Options", "nosniff");
        // As telas (outra origem, no Vercel) mostram estas fotos.
        response.setHeader("Cross-Origin-Resource-Policy", "cross-origin");
        return true;
    }

    static boolean ehImagem(String caminho) {
        String nome = caminho.substring(caminho.lastIndexOf('/') + 1);
        int ponto = nome.lastIndexOf('.');
        return ponto > 0 && EXTENSOES_DE_IMAGEM.contains(nome.substring(ponto + 1).toLowerCase(Locale.ROOT));
    }
}
