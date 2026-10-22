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
 * O application.properties de produção: a conta da demonstração — a da Ana,
 * que o botão da página inicial abre — e a da apresentação têm de subir sem
 * variável nenhuma no Render, a da apresentação com a senha que a equipe
 * escolheu.
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
    void aDemonstracaoSobeSemVariavelNenhuma() throws IOException {
        assertThat(resolver("rehabit.demo.ativar", Map.of())).isEqualTo("true");
        // Quem já tinha REHABIT_DEMO=true no Render segue igual.
        assertThat(resolver("rehabit.demo.ativar", Map.of("REHABIT_DEMO", "true"))).isEqualTo("true");
        assertThat(resolver("rehabit.demo.ativar", Map.of("REHABIT_DEMO", "false"))).isEqualTo("false");
        // A senha é a que o botão da página inicial preenche.
        assertThat(resolver("rehabit.demo.senha", Map.of())).isEqualTo("Rehabit@2026");
    }

    @Test
    void aApresentacaoSobeSemVariavelNenhuma() throws IOException {
        assertThat(resolver("rehabit.apresentacao.ativar", Map.of())).isEqualTo("true");
        // Nem depende da demonstração: cada uma tem a sua variável.
        assertThat(resolver("rehabit.apresentacao.ativar", Map.of("REHABIT_DEMO", "false"))).isEqualTo("true");
        assertThat(resolver("rehabit.apresentacao.ativar", Map.of("REHABIT_APRESENTACAO", "false"))).isEqualTo("false");
    }

    @Test
    void oCommitNoArVemDoRender() throws IOException {
        assertThat(resolver("rehabit.versao", Map.of("RENDER_GIT_COMMIT", "276664bfa2f8fd4dc5b26c023d447c1946ec4609")))
                .isEqualTo("276664bfa2f8fd4dc5b26c023d447c1946ec4609");
        assertThat(resolver("rehabit.versao", Map.of())).isEmpty();
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
