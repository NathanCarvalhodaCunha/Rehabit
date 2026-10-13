package com.rehabit.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;

/**
 * Limites por IP (e o teto global do pareamento) das rotas públicas que um
 * robô teria interesse em martelar. Os limites por conta e por destinatário
 * ficam nos services, que têm o e-mail já normalizado em mãos.
 *
 * É um interceptor do Spring MVC, e não um filtro, para o 429 sair pelo
 * mesmo caminho das outras respostas de erro — com os cabeçalhos de CORS,
 * que o navegador exige para mostrar a mensagem ao site.
 */
@Component
public class LimitePorIpInterceptor implements HandlerInterceptor {

    /**
     * Rota limitada. {@code soFalhas}: a chamada só gasta a cota quando dá
     * errado (o código de pareamento incorreto), e não a cada tentativa.
     */
    private record Rota(String metodo, String caminho, boolean soFalhas, List<Limite> limites) {
        /**
         * Compara com a rota que o Spring de fato escolheu, e não com a URL
         * crua: o Spring decodifica o caminho antes de rotear, então
         * "/api/auth/l%6Fgin" chegava ao login sem nunca bater em
         * "/api/auth/login" aqui — e o limite por IP não contava nada. HEAD
         * vale como GET pelo mesmo motivo: o Spring o entrega ao método GET.
         */
        boolean atende(HttpServletRequest request) {
            Object rotaEscolhida = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String caminhoPedido = rotaEscolhida != null ? rotaEscolhida.toString() : request.getRequestURI();
            String metodoPedido = "HEAD".equals(request.getMethod()) ? "GET" : request.getMethod();
            return metodo.equals(metodoPedido) && caminho.equals(caminhoPedido);
        }
    }

    private static final List<Rota> ROTAS = List.of(
            new Rota("POST", "/api/auth/login", false, List.of(Limite.LOGIN_POR_IP)),
            new Rota("POST", "/api/auth/register", false, List.of(Limite.CADASTRO_POR_IP)),
            new Rota("POST", "/api/auth/verificar-email/enviar", false, List.of(Limite.EMAIL_POR_IP)),
            new Rota("POST", "/api/auth/esqueci-senha", false, List.of(Limite.EMAIL_POR_IP)),
            new Rota("POST", "/api/auth/verificar-email/confirmar", false, List.of(Limite.CODIGO_POR_IP)),
            new Rota("POST", "/api/auth/recuperar-senha", false, List.of(Limite.CODIGO_POR_IP)),
            new Rota("GET", "/api/auth/recuperar-senha/validar", false, List.of(Limite.CODIGO_POR_IP)),
            new Rota("POST", "/api/dispositivos/parear", true,
                    List.of(Limite.PAREAMENTO_POR_IP, Limite.PAREAMENTO_GLOBAL)),
            new Rota("POST", "/api/uploads", false, List.of(Limite.UPLOAD_POR_IP)),
            new Rota("POST", "/api/auth/cadastro/foto", false, List.of(Limite.UPLOAD_POR_IP)));

    private final LimiteDeTentativas limites;
    private final IpDoCliente ipDoCliente;

    public LimitePorIpInterceptor(LimiteDeTentativas limites, IpDoCliente ipDoCliente) {
        this.limites = limites;
        this.ipDoCliente = ipDoCliente;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Rota rota = rotaDe(request);
        if (rota == null) {
            return true;
        }
        String ip = ipDoCliente.de(request);
        for (Limite limite : rota.limites()) {
            if (rota.soFalhas()) {
                limites.exigirFolga(limite, chave(limite, ip));
            } else {
                limites.consumir(limite, chave(limite, ip));
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        Rota rota = rotaDe(request);
        if (rota == null || !rota.soFalhas() || !recusada(response.getStatus())) {
            return;
        }
        String ip = ipDoCliente.de(request);
        rota.limites().forEach(limite -> limites.contar(limite, chave(limite, ip)));
    }

    /** Erro de quem chamou (código errado, vencido, corpo inválido). Falha do servidor não conta. */
    private static boolean recusada(int status) {
        return status >= 400 && status < 500 && status != HttpStatus.TOO_MANY_REQUESTS.value();
    }

    private static String chave(Limite limite, String ip) {
        return limite == Limite.PAREAMENTO_GLOBAL ? LimiteDeTentativas.TODOS : ip;
    }

    private static Rota rotaDe(HttpServletRequest request) {
        return ROTAS.stream().filter(rota -> rota.atende(request)).findFirst().orElse(null);
    }
}
