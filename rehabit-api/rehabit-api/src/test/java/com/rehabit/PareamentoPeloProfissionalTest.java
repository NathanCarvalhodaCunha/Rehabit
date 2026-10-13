package com.rehabit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehabit.demo.ContaDeDemonstracao;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.repository.DispositivoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem está com o goniômetro na mão costuma ser o fisioterapeuta: ele também
 * gera o código de pareamento e vê os aparelhos da clínica. Revogar continua
 * com a clínica, e a demonstração pública continua sem parear — a senha dela
 * está no site, e qualquer visitante mandaria comandos a um aparelho real.
 */
class PareamentoPeloProfissionalTest extends TesteDeIntegracao {

    @Autowired
    private DispositivoRepository dispositivos;

    private final ObjectMapper json = new ObjectMapper();

    private String gerarCodigo(Integer idClinica, String token) throws Exception {
        String corpo = mvc.perform(post("/api/dispositivos/pareamento?idClinica=" + idClinica)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo", matchesPattern("\\d{6}")))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("codigo").asText();
    }

    private Integer parear(String codigo) throws Exception {
        String corpo = mvc.perform(post("/api/dispositivos/parear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("idDispositivo").asInt();
    }

    @Test
    void oProfissionalGeraOCodigoEOAparelhoEntraNaClinicaDele() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta profissional = novoProfissional(clinica);

        Integer idDispositivo = parear(gerarCodigo(clinica.getId(), tokenDe(profissional)));

        assertThat(dispositivos.findById(idDispositivo).orElseThrow().getIdClinica()).isEqualTo(clinica.getId());
        mvc.perform(get("/api/dispositivos?idClinica=" + clinica.getId()).header("Authorization", tokenDe(profissional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ativo").value(true));
    }

    @Test
    void oProfissionalNaoPareiaNemListaEmOutraClinica() throws Exception {
        Fisioterapeuta profissional = novoProfissional(novaClinica());
        Clinica outra = novaClinica();

        mvc.perform(post("/api/dispositivos/pareamento?idClinica=" + outra.getId())
                        .header("Authorization", tokenDe(profissional)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dispositivos?idClinica=" + outra.getId()).header("Authorization", tokenDe(profissional)))
                .andExpect(status().isForbidden());
    }

    @Test
    void soAClinicaRevoga() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta profissional = novoProfissional(clinica);
        Integer idDispositivo = parear(gerarCodigo(clinica.getId(), tokenDe(clinica)));

        mvc.perform(delete("/api/dispositivos/" + idDispositivo).header("Authorization", tokenDe(profissional)))
                .andExpect(status().isForbidden());
        assertThat(dispositivos.findById(idDispositivo).orElseThrow().isAtivo()).isTrue();

        mvc.perform(delete("/api/dispositivos/" + idDispositivo).header("Authorization", tokenDe(clinica)))
                .andExpect(status().isNoContent());
        assertThat(dispositivos.findById(idDispositivo).orElseThrow().isAtivo()).isFalse();
    }

    @Test
    void aDemonstracaoPublicaContinuaSemParear() throws Exception {
        Clinica demo = novaClinica();
        demo.setEmail(ContaDeDemonstracao.EMAIL_CLINICA);
        demo = clinicas.save(demo);
        Fisioterapeuta ana = novoProfissional(demo);

        for (String token : new String[] {tokenDe(demo), tokenDe(ana)}) {
            mvc.perform(post("/api/dispositivos/pareamento?idClinica=" + demo.getId())
                            .header("Authorization", token)
                            .header("Origin", "https://rehabit.com.br"))
                    .andExpect(status().isForbidden())
                    // Antes a recusa saía sem CORS e o site mostrava "Não foi
                    // possível falar com o servidor" no lugar deste aviso.
                    .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                    .andExpect(jsonPath("$.mensagem").value(containsString("demonstração")));
        }
        assertThat(dispositivos.findByIdClinicaOrderByCriadoEmDesc(demo.getId())).isEmpty();
    }
}
