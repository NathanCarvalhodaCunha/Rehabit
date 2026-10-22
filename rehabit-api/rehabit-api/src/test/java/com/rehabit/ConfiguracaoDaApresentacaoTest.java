package com.rehabit;

import com.rehabit.security.PoliticaDeSenha;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O application.properties de produção: no Render a demonstração já está
 * ligada (REHABIT_DEMO=true), e a conta da apresentação — onde está a Mariana,
 * a demonstração da página inicial — tem de subir com ela, sem variável nova.
 */
class ConfiguracaoDaApresentacaoTest {

    /** Resolve uma chave como o Spring, com as variáveis de ambiente dadas. */
    private static String resolver(String chave, Map<String, Object> variaveis) throws IOException {
        StandardEnvironment ambiente = new StandardEnvironment();
        ambiente.getPropertySources().addFirst(new MapPropertySource("variaveis", variaveis));
        ambiente.getPropertySources().addLast(new ResourcePropertySource(new ClassPathResource("application.properties")));
        return ambiente.getProperty(chave);
    }

    @Test
    void aApresentacaoSobeJuntoComADemonstracao() throws IOException {
        assertThat(resolver("rehabit.apresentacao.ativar", Map.of("REHABIT_DEMO", "true"))).isEqualTo("true");
        assertThat(resolver("rehabit.apresentacao.ativar", Map.of())).isEqualTo("false");
        // A variável própria manda nos dois sentidos.
        assertThat(resolver("rehabit.apresentacao.ativar",
                Map.of("REHABIT_DEMO", "true", "REHABIT_APRESENTACAO", "false"))).isEqualTo("false");
        assertThat(resolver("rehabit.apresentacao.ativar", Map.of("REHABIT_APRESENTACAO", "true"))).isEqualTo("true");
    }

    @Test
    void semVariavelASenhaEhADaBancaEPassaNaPolitica() throws IOException {
        String senha = resolver("rehabit.apresentacao.senha", Map.of());

        assertThat(senha).isEqualTo("Rehabit1234@");
        assertThat(PoliticaDeSenha.problema(senha)).isNull();
        assertThat(resolver("rehabit.apresentacao.senha", Map.of("REHABIT_APRESENTACAO_SENHA", "Outra-Senha-Da-Banca-9")))
                .isEqualTo("Outra-Senha-Da-Banca-9");
        // A da demonstração, que está no site, é outra: ela não abre as contas comuns.
        assertThat(resolver("rehabit.demo.senha", Map.of())).isNotEqualTo(senha);
    }
}
