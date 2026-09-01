package com.rehabit.email;

import com.rehabit.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Verifica se um e-mail informado no cadastro pode existir de verdade, em
 * três camadas, da mais barata para a mais cara:
 *
 * 1. formato — regra de escrita mais rígida que a do @Email do Bean Validation,
 *    que aceita coisas como "a@b" (sem domínio de topo);
 * 2. domínio descartável — bloqueia serviços de e-mail temporário, que existem
 *    mas somem em minutos e deixam a conta órfã;
 * 3. DNS — consulta o registro MX do domínio. Sem MX (nem A/AAAA, que a
 *    RFC 5321 aceita como alternativa), aquele domínio não recebe e-mail e
 *    o endereço não pode existir.
 *
 * A prova final de que a caixa de entrada existe mesmo é o código enviado
 * por e-mail no cadastro (VerificacaoEmailService); estas três camadas
 * evitam gastar um envio com endereço que já se sabe inválido.
 */
@Service
public class ValidadorEmailService {

    private static final Logger log = LoggerFactory.getLogger(ValidadorEmailService.class);

    // Sem acentos, sem espaços, com domínio de topo de pelo menos 2 letras e
    // sem pontos no começo/fim ou repetidos.
    private static final Pattern FORMATO = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,}$");

    // Erros de digitação comuns: o domínio existe na cabeça de quem digitou,
    // mas não no DNS. Avisar qual era o certo evita o "meu e-mail é válido!".
    private static final Map<String, String> DOMINIOS_PARECIDOS = Map.ofEntries(
            Map.entry("gmail.con", "gmail.com"),
            Map.entry("gmail.co", "gmail.com"),
            Map.entry("gmail.cm", "gmail.com"),
            Map.entry("gmial.com", "gmail.com"),
            Map.entry("gmai.com", "gmail.com"),
            Map.entry("gamil.com", "gmail.com"),
            Map.entry("hotmail.con", "hotmail.com"),
            Map.entry("hotmial.com", "hotmail.com"),
            Map.entry("hotmail.co", "hotmail.com"),
            Map.entry("outlook.con", "outlook.com"),
            Map.entry("outllok.com", "outlook.com"),
            Map.entry("yahoo.con", "yahoo.com"),
            Map.entry("uol.com", "uol.com.br"),
            Map.entry("bol.com", "bol.com.br"));

    private final Set<String> dominiosDescartaveis;
    private final boolean validarDns;

    /** Cache do resultado do DNS: o mesmo domínio se repete muito entre cadastros. */
    private final Map<String, Boolean> cacheDns = new ConcurrentHashMap<>();

    public ValidadorEmailService(@Value("${rehabit.email.validar-dns:true}") boolean validarDns) {
        this.validarDns = validarDns;
        this.dominiosDescartaveis = carregarDescartaveis();
    }

    /** Deixa o e-mail no formato em que ele é gravado e comparado: sem espaços e em minúsculas. */
    public String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * Valida o e-mail e devolve a versão normalizada.
     *
     * @throws AuthException (400) com uma mensagem explicando o problema.
     */
    public String validarENormalizar(String emailBruto) {
        String email = normalizar(emailBruto);

        if (email == null || email.isBlank()) {
            throw erro("Informe um e-mail.");
        }
        if (email.length() > 150) {
            throw erro("Este e-mail é longo demais.");
        }
        if (!FORMATO.matcher(email).matches() || email.contains("..")) {
            throw erro("Este e-mail não parece válido. Confira se está escrito corretamente.");
        }

        String dominio = email.substring(email.indexOf('@') + 1);

        String sugestao = DOMINIOS_PARECIDOS.get(dominio);
        if (sugestao != null) {
            throw erro("O domínio \"@" + dominio + "\" não existe. Você quis dizer \"@" + sugestao + "\"?");
        }

        if (dominiosDescartaveis.contains(dominio)) {
            throw erro("E-mails temporários não são aceitos. Use um e-mail permanente.");
        }

        if (validarDns && !dominioRecebeEmail(dominio)) {
            throw erro("O domínio \"@" + dominio + "\" não recebe e-mails. Confira o endereço informado.");
        }

        return email;
    }

    /**
     * true quando o domínio tem servidor de e-mail publicado no DNS.
     *
     * Em caso de falha da própria consulta (rede fora do ar, timeout), devolve
     * true: barrar um cadastro legítimo porque o DNS piscou é pior do que
     * deixar passar um domínio inexistente — que ainda vai esbarrar no código
     * de confirmação enviado por e-mail.
     */
    private boolean dominioRecebeEmail(String dominio) {
        Boolean emCache = cacheDns.get(dominio);
        if (emCache != null) {
            return emCache;
        }

        boolean resultado = consultarDns(dominio);

        // Limite bobo de tamanho só para o cache não crescer sem fim.
        if (cacheDns.size() > 1000) {
            cacheDns.clear();
        }
        cacheDns.put(dominio, resultado);
        return resultado;
    }

    private boolean consultarDns(String dominio) {
        Hashtable<String, String> ambiente = new Hashtable<>();
        ambiente.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        ambiente.put("com.sun.jndi.dns.timeout.initial", "3000");
        ambiente.put("com.sun.jndi.dns.timeout.retries", "1");

        DirContext contexto = null;
        try {
            contexto = new InitialDirContext(ambiente);

            Attributes mx = contexto.getAttributes(dominio, new String[] { "MX" });
            Attribute registroMx = mx.get("MX");
            if (registroMx != null && registroMx.size() > 0) {
                return true;
            }

            // Sem MX, a RFC 5321 manda tentar entregar no próprio endereço do domínio.
            Attributes enderecos = contexto.getAttributes(dominio, new String[] { "A", "AAAA" });
            return enderecos.get("A") != null || enderecos.get("AAAA") != null;
        } catch (NameNotFoundException ex) {
            return false; // o domínio não existe no DNS
        } catch (NamingException ex) {
            log.warn("Consulta de DNS falhou para \"{}\" ({}). Cadastro liberado por precaução.",
                    dominio, ex.getMessage());
            return true;
        } finally {
            if (contexto != null) {
                try {
                    contexto.close();
                } catch (NamingException ignorado) {
                    // fechar o contexto é só higiene; falhar aqui não muda o resultado
                }
            }
        }
    }

    private Set<String> carregarDescartaveis() {
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(
                new ClassPathResource("emails-descartaveis.txt").getInputStream(), StandardCharsets.UTF_8))) {
            return leitor.lines()
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(linha -> !linha.isEmpty() && !linha.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException ex) {
            log.warn("Não foi possível ler a lista de domínios descartáveis; o bloqueio ficará desligado.", ex);
            return Set.of();
        }
    }

    private AuthException erro(String mensagem) {
        return new AuthException(mensagem, HttpStatus.BAD_REQUEST);
    }
}
