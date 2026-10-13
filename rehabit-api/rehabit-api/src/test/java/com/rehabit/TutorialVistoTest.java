package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * As rotas de "tutorial visto" gravavam no id do caminho sem olhar quem
 * pedia: qualquer conta logada marcava a de qualquer outra. Agora cada um só
 * marca o próprio.
 */
class TutorialVistoTest extends TesteDeIntegracao {

    @Test
    void cadaContaSoMarcaOProprioTutorial() throws Exception {
        Clinica clinica = novaClinica();
        Clinica outra = novaClinica();
        Fisioterapeuta daOutra = novoProfissional(outra);
        Fisioterapeuta daClinica = novoProfissional(clinica);

        mvc.perform(put("/api/clinicas/{id}/tutorial-visto", outra.getId()).header("Authorization", tokenDe(clinica)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/fisioterapeutas/{id}/tutorial-visto", daOutra.getId())
                        .header("Authorization", tokenDe(clinica)))
                .andExpect(status().isForbidden());
        // Nem a clínica marca pelo profissional dela: o tutorial é de quem o vê.
        mvc.perform(put("/api/fisioterapeutas/{id}/tutorial-visto", daClinica.getId())
                        .header("Authorization", tokenDe(clinica)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/clinicas/{id}/tutorial-visto", clinica.getId()).header("Authorization", tokenDe(daClinica)))
                .andExpect(status().isForbidden());

        assertThat(clinicas.findById(outra.getId()).orElseThrow().isTutorialVisto()).isFalse();
        assertThat(fisioterapeutas.findById(daOutra.getId()).orElseThrow().isTutorialVisto()).isFalse();
        assertThat(fisioterapeutas.findById(daClinica.getId()).orElseThrow().isTutorialVisto()).isFalse();
    }

    @Test
    void aPropriaContaMarca() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta profissional = novoProfissional(clinica);

        mvc.perform(put("/api/clinicas/{id}/tutorial-visto", clinica.getId()).header("Authorization", tokenDe(clinica)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/fisioterapeutas/{id}/tutorial-visto", profissional.getId())
                        .header("Authorization", tokenDe(profissional)))
                .andExpect(status().isOk());

        assertThat(clinicas.findById(clinica.getId()).orElseThrow().isTutorialVisto()).isTrue();
        assertThat(fisioterapeutas.findById(profissional.getId()).orElseThrow().isTutorialVisto()).isTrue();
    }
}
