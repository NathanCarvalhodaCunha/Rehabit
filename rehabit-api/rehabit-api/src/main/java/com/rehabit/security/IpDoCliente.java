package com.rehabit.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Endereço de quem fez a requisição, para os limites por IP.
 *
 * No Render a conexão chega do proxy dele, então getRemoteAddr() é o mesmo
 * para todo mundo; o endereço real vem no X-Forwarded-For. Mas esse cabeçalho
 * é uma lista que o próprio cliente pode começar: cada proxy só acrescenta,
 * no fim, o endereço de quem falou com ele. Por isso vale o último endereço
 * (ou o N-ésimo a partir do fim, com N proxies de confiança na frente), nunca
 * o primeiro — confiar no primeiro deixaria um robô trocar de "IP" a cada
 * tentativa só mudando o cabeçalho.
 *
 * Sem proxy na frente (rodando local), o cabeçalho inteiro é do cliente e
 * fica ignorado.
 */
@Component
public class IpDoCliente {

    private static final Logger log = LoggerFactory.getLogger(IpDoCliente.class);

    private final int proxiesConfiaveis;

    public IpDoCliente(@Value("${rehabit.rede.proxies-confiaveis:}") String proxiesConfiaveis,
                       @Value("${RENDER:false}") boolean noRender) {
        this.proxiesConfiaveis = proxiesConfiaveis == null || proxiesConfiaveis.isBlank()
                ? (noRender ? 1 : 0)
                : Integer.parseInt(proxiesConfiaveis.trim());
        if (this.proxiesConfiaveis < 0) {
            throw new IllegalStateException("rehabit.rede.proxies-confiaveis não pode ser negativo.");
        }
        log.info(this.proxiesConfiaveis == 0
                ? "IP do cliente: endereço da conexão (X-Forwarded-For ignorado)."
                : "IP do cliente: X-Forwarded-For, com {} proxy(s) de confiança na frente.", this.proxiesConfiaveis);
    }

    public String de(HttpServletRequest request) {
        String daConexao = request.getRemoteAddr();
        if (proxiesConfiaveis == 0) {
            return daConexao;
        }
        String cabecalho = request.getHeader("X-Forwarded-For");
        if (cabecalho == null || cabecalho.isBlank()) {
            return daConexao;
        }
        List<String> enderecos = Arrays.stream(cabecalho.split(","))
                .map(String::trim)
                .filter(endereco -> !endereco.isEmpty())
                .toList();
        if (enderecos.isEmpty()) {
            return daConexao;
        }
        // Com menos endereços que proxies, fica o mais antigo que houver.
        return enderecos.get(Math.max(0, enderecos.size() - proxiesConfiaveis));
    }
}
