package com.rehabit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehabit.demo.ContaDeDemonstracao;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String STREAM = "/api/goniometro/stream";
    private static final String EMISSAO_DE_INGRESSO = "/api/goniometro/stream/ingresso";

    private final JwtService jwtService;
    private final IngressoDoStream ingressos;
    private final ClinicaRepository clinicaRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(JwtService jwtService, IngressoDoStream ingressos,
                                   ClinicaRepository clinicaRepository,
                                   FisioterapeutaRepository fisioterapeutaRepository) {
        this.jwtService = jwtService;
        this.ingressos = ingressos;
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
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

        JwtService.TokenDados dados = identificar(request);

        if (dados == null || !contaAindaValida(dados)) {
            escreverNaoAutenticado(response);
            return;
        }

        // O token do goniômetro dura um ano, fica gravado num aparelho na
        // parede e qualquer clínica gera um pareando o próprio aparelho. Fora
        // das rotas dele, o id do dispositivo caía nos services como se fosse
        // o de um profissional — o de mesmo número, de outra clínica.
        if ("DISPOSITIVO".equals(dados.tipo()) && !ehRotaDoAparelho(request)) {
            escreverErro(response, HttpStatus.FORBIDDEN,
                    "Este token é de um goniômetro e só serve para enviar leituras.");
            return;
        }

        if (alteraDados(request) && ehDaDemonstracao(dados)) {
            escreverErro(response, HttpStatus.FORBIDDEN,
                    "Esta é a conta de demonstração: dá para ver tudo, mas nada pode ser alterado.");
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
     * O token é stateless e vale 30 dias: assinatura e validade em dia não
     * dizem se a conta ainda existe, nem se a senha mudou depois que ele saiu.
     * Sem esta consulta, uma clínica ou um profissional excluídos seguiriam
     * usando a API com o token que já tinham até ele vencer — e quem trocou a
     * senha depois de uma invasão continuaria com o invasor lá dentro.
     *
     * É uma busca por chave primária a mais por requisição, e fica sem cache
     * de propósito — um cache reabriria justamente a janela que isto fecha.
     *
     * Token de dispositivo não passa por aqui: DispositivoService
     * .exigirDispositivoAtivo já confere o aparelho a cada leitura, e é o que
     * dá efeito imediato ao botão "Revogar".
     */
    private boolean contaAindaValida(JwtService.TokenDados dados) {
        if ("CLINICA".equals(dados.tipo())) {
            return clinicaRepository.findById(dados.id())
                    .map(c -> emitidoDepoisDaUltimaTroca(dados, c.getCredenciaisAlteradasEm()))
                    .orElse(false);
        }
        if ("FISIOTERAPEUTA".equals(dados.tipo())) {
            return fisioterapeutaRepository.findById(dados.id())
                    .map(f -> !f.isExcluido() && emitidoDepoisDaUltimaTroca(dados, f.getCredenciaisAlteradasEm()))
                    .orElse(false);
        }
        return true;
    }

    /**
     * O token guarda o instante em milissegundos (veja JwtService.emitidoEm),
     * e o banco grava a troca com mais precisão: corta-se a troca no
     * milissegundo, senão o token novo, pedido logo em seguida, pareceria
     * anterior a ela.
     */
    private static boolean emitidoDepoisDaUltimaTroca(JwtService.TokenDados dados, Instant credenciaisAlteradasEm) {
        return credenciaisAlteradasEm == null
                || !dados.emitidoEm().isBefore(credenciaisAlteradasEm.truncatedTo(ChronoUnit.MILLIS));
    }

    /** O que o firmware chama depois de pareado — e só isso. */
    private static boolean ehRotaDoAparelho(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "POST".equals(request.getMethod())
                && (path.equals("/api/goniometro/telemetria") || path.equals("/api/goniometro/leitura"));
    }

    /**
     * Pedir o ingresso do stream é POST, mas só serve para assistir ao
     * goniômetro: a conta de demonstração também precisa dele.
     */
    private static boolean alteraDados(HttpServletRequest request) {
        String metodo = request.getMethod();
        return !"GET".equals(metodo) && !"HEAD".equals(metodo)
                && !request.getRequestURI().equals(EMISSAO_DE_INGRESSO);
    }

    /**
     * A senha da conta de demonstração está publicada na página inicial, então
     * ela é só de leitura: a clínica e os profissionais dela consultam tudo,
     * mas qualquer alteração — inclusive excluir a conta ou trocar a senha —
     * é recusada aqui, antes de chegar aos controllers. Só roda em requisição
     * que altera dados, então a consulta a mais não pesa na navegação.
     *
     * Token de dispositivo fica de fora: um goniômetro pareado com a clínica
     * de demonstração pode mandar leituras normalmente.
     */
    private boolean ehDaDemonstracao(JwtService.TokenDados dados) {
        Integer idClinica = switch (dados.tipo()) {
            case "CLINICA" -> dados.id();
            case "FISIOTERAPEUTA" -> fisioterapeutaRepository.findById(dados.id())
                    .map(Fisioterapeuta::getIdClinica)
                    .orElse(null);
            default -> null;
        };
        return idClinica != null && clinicaRepository.findById(idClinica)
                .map(c -> ContaDeDemonstracao.EMAIL_CLINICA.equalsIgnoreCase(c.getEmail()))
                .orElse(false);
    }

    /**
     * O token vem no cabeçalho Authorization. A exceção é o SSE do
     * goniômetro: o EventSource do navegador não permite cabeçalhos
     * customizados, então ali — e só ali — a URL pode levar uma credencial.
     * Mas não o token de sessão, que vale 30 dias e iria parar em log de
     * proxy: só o ingresso de uso único (IngressoDoStream), preso à clínica
     * do parâmetro idClinica. Token de sessão na URL é ignorado, e sem outra
     * credencial a resposta é 401.
     */
    private JwtService.TokenDados identificar(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return jwtService.validar(header.substring(7));
        }
        if (request.getRequestURI().equals(STREAM) && "GET".equals(request.getMethod())) {
            return ingressos.resgatar(request.getParameter("ingresso"), request.getParameter("idClinica"));
        }
        return null;
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
        // O ping que impede a instância gratuita de hibernar chega sem token
        // nenhum (veja HealthController e o workflow manter-api-acordada.yml).
        // Sem esta linha ele tomaria 401: este filtro roda antes do
        // roteamento, então nem o permitAll() do SecurityConfig nem o
        // controller chegam a ser consultados — e o ping seguiria acordando a
        // API, mas acusando falha a cada dez minutos.
        if (path.equals("/api/health") && "GET".equals(metodo)) {
            return true;
        }
        // As fotos são lidas por <img> e CSS, que não mandam cabeçalho de
        // autorização. O nome é um UUID sorteado (ninguém chega a uma foto
        // sem já ter visto o endereço dela), e o arquivo sai do disco sem
        // metadados e com cabeçalhos que impedem rodar como página (veja
        // CabecalhosDosUploads).
        if (path.startsWith("/uploads/") && "GET".equals(metodo)) {
            return true;
        }
        // O goniômetro ainda não tem token quando vai parear — é justamente o
        // que ele busca aqui. Protegido pelo código de uso único e curta
        // validade, não por autenticação.
        // O envio de fotos (/api/uploads) já esteve aqui e não está mais:
        // exige login. A foto do cadastro vai por /api/auth/cadastro/foto.
        return path.equals("/api/dispositivos/parear") && "POST".equals(metodo);
    }

    private void escreverNaoAutenticado(HttpServletResponse response) throws IOException {
        escreverErro(response, HttpStatus.UNAUTHORIZED, "Sessão inválida ou expirada. Faça login novamente.");
    }

    /**
     * A recusa sai daqui, antes de chegar a qualquer controller, então o
     * cabeçalho de CORS do @CrossOrigin não vem junto. Sem ele, o navegador
     * esconde a resposta do site: a sessão vencida (401) não levava ao login
     * e o aviso da conta de demonstração (403) virava "Não foi possível falar
     * com o servidor". Origem "*", como nos controllers: a credencial vai no
     * cabeçalho Authorization, nunca em cookie.
     */
    private void escreverErro(HttpServletResponse response, HttpStatus status, String mensagem) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, String> corpo = new HashMap<>();
        corpo.put("mensagem", mensagem);
        response.getWriter().write(objectMapper.writeValueAsString(corpo));
    }
}
