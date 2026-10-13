package com.rehabit;

import com.rehabit.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A chave que assina os tokens nunca pode cair num valor conhecido: em
 * produção, sem REHABIT_JWT_SECRET a API não sobe; fora dela, sobe com uma
 * chave sorteada a cada execução. Antes havia um valor-padrão no
 * application.properties, e quem lesse o repositório forjava token de
 * qualquer conta.
 *
 * Sobe só o JwtService, com os mesmos arquivos de configuração da API, para
 * ver o que a configuração de verdade faz em cada ambiente.
 */
class ChaveDosTokensNaSubidaTest {

    @Configuration
    @Import(JwtService.class)
    static class SoAChave {
    }

    @BeforeEach
    void semAsVariaveisDaMaquina() {
        // Se a máquina que roda os testes tiver estas variáveis, o que se
        // testaria é ela, e não a configuração.
        assumeTrue(System.getenv("REHABIT_JWT_SECRET") == null, "REHABIT_JWT_SECRET definida no ambiente");
        assumeTrue(System.getenv("RENDER") == null, "RENDER definida no ambiente");
    }

    /**
     * As propriedades entram como as variáveis de ambiente entrariam: o
     * application.properties as lê pelos placeholders (REHABIT_JWT_SECRET,
     * RENDER) e tem precedência sobre elas para o resto.
     */
    private static ConfigurableApplicationContext subir(String perfil, String... propriedades) {
        SpringApplicationBuilder api = new SpringApplicationBuilder(SoAChave.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .properties(propriedades);
        if (perfil != null) {
            api.profiles(perfil);
        }
        return api.run();
    }

    @Test
    void perfilDeProducaoSemChaveNaoSobe() {
        assertThatThrownBy(() -> subir("cloud").close())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("REHABIT_JWT_SECRET não está definida");
    }

    @Test
    void noRenderSemChaveNaoSobeMesmoSemOPerfil() {
        assertThatThrownBy(() -> subir(null, "RENDER=true").close())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("REHABIT_JWT_SECRET não está definida");
    }

    @Test
    void chaveCurtaNaoSobe() {
        assertThatThrownBy(() -> subir(null, "REHABIT_JWT_SECRET=curta-demais-para-hmac").close())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("o mínimo é 32");
    }

    @Test
    void foraDeProducaoSemChaveCadaSubidaSorteiaAPropria() {
        try (ConfigurableApplicationContext primeira = subir(null);
             ConfigurableApplicationContext segunda = subir(null)) {
            JwtService daPrimeira = primeira.getBean(JwtService.class);
            JwtService daSegunda = segunda.getBean(JwtService.class);

            String token = daPrimeira.gerarToken(7, "CLINICA");

            assertThat(daPrimeira.validar(token)).isNotNull();
            assertThat(daSegunda.validar(token))
                    .as("um token assinado por outra subida não pode valer aqui")
                    .isNull();
        }
    }

    @Test
    void chaveConfiguradaComTamanhoBomSobeEValeEntreSubidas() {
        String chave = "REHABIT_JWT_SECRET=uma-chave-de-teste-com-bem-mais-de-trinta-e-dois-bytes";
        try (ConfigurableApplicationContext primeira = subir("cloud", chave);
             ConfigurableApplicationContext segunda = subir("cloud", chave)) {
            String token = primeira.getBean(JwtService.class).gerarToken(7, "CLINICA");
            assertThat(segunda.getBean(JwtService.class).validar(token)).isNotNull();
        }
    }
}
