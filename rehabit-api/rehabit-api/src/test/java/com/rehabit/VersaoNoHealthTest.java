package com.rehabit;

import com.rehabit.controller.HealthController;
import com.rehabit.service.ManterApiAcordada;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O /api/health diz qual commit está no ar: é por ele que se percebe, de fora,
 * que o Render não publicou o último merge (o site, na Vercel, já publicou).
 */
@TestPropertySource(properties = "rehabit.versao=" + VersaoNoHealthTest.COMMIT)
class VersaoNoHealthTest extends TesteDeIntegracao {

    static final String COMMIT = "276664bfa2f8fd4dc5b26c023d447c1946ec4609";

    @Test
    void oHealthDizQualCommitEstaNoArSemPedirLogin() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.versao").value(COMMIT));
    }

    @Test
    void foraDoRenderAVersaoVemVazia() {
        HealthController semRender = new HealthController(Mockito.mock(ManterApiAcordada.class), "");

        assertThat(semRender.health().getBody()).containsEntry("versao", null);
    }
}
