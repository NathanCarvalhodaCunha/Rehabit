package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Depois de excluído, o profissional some de tudo que serve para escolher ou
 * encontrar alguém, mas continua em tudo que conta o que já aconteceu.
 */
class ProfissionalExcluidoNasTelasTest extends TesteDeIntegracao {

    private Clinica clinica;
    private Fisioterapeuta ana;
    private Fisioterapeuta carlos;

    /** Ana atendeu uma sessão este mês e depois foi excluída, passando tudo para o Carlos. */
    private void anaAtendeuEFoiExcluida() throws Exception {
        clinica = novaClinica();
        ana = novoProfissional(clinica);
        carlos = novoProfissional(clinica);
        Paciente joao = novoPaciente(ana);
        novaSessao(joao, ana, LocalDate.now().withDayOfMonth(1));

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .param("transferirPara", carlos.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk());
    }

    @Test
    void someDaListaDeProfissionais() throws Exception {
        anaAtendeuEFoiExcluida();

        mvc.perform(get("/api/fisioterapeutas").param("idClinica", clinica.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(ana.getId()))))
                .andExpect(jsonPath("$[*].id", hasItem(carlos.getId())));
    }

    @Test
    void someDaBusca() throws Exception {
        anaAtendeuEFoiExcluida();

        mvc.perform(get("/api/busca").param("q", ana.getNome())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tipo == 'PROFISSIONAL')].id", not(hasItem(ana.getId()))));
    }

    @Test
    void naoContaComoProfissionalDaClinica() throws Exception {
        anaAtendeuEFoiExcluida();

        mvc.perform(get("/api/clinicas/{id}", clinica.getId()).header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profissionaisAtivos").value(1));

        mvc.perform(get("/api/estatisticas").header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.rotulo == 'Profissionais')].valor", hasItem("1")));
    }

    @Test
    void asSessoesDeleContinuamNosTotaisDaClinica() throws Exception {
        anaAtendeuEFoiExcluida();

        mvc.perform(get("/api/clinicas/{id}", clinica.getId()).header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessoesEsteMes").value(1));
    }

    @Test
    void oHistoricoDizQueQuemAtendeuTeveAContaExcluida() throws Exception {
        anaAtendeuEFoiExcluida();

        mvc.perform(get("/api/agendamentos/realizadas").param("idClinica", clinica.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idFisioterapeuta").value(ana.getId()))
                .andExpect(jsonPath("$[0].nomeFisioterapeuta").value(ana.getNome() + " (conta excluída)"));
    }

    @Test
    void oTokenQueEleJaTinhaDeixaDeValer() throws Exception {
        anaAtendeuEFoiExcluida();

        // O próprio perfil, que um token válido dela sempre pôde ler: assim o
        // 401 só pode vir da autenticação, e não de falta de permissão.
        mvc.perform(get("/api/fisioterapeutas/{id}", ana.getId()).header(AUTHORIZATION, tokenDe(ana)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Guarda para a mudança no filtro de autenticação: ele passa a consultar
     * o banco a cada requisição, e um profissional ativo não pode cair junto.
     */
    @Test
    void oTokenDeUmProfissionalAtivoContinuaValendo() throws Exception {
        anaAtendeuEFoiExcluida();

        mvc.perform(get("/api/fisioterapeutas/{id}", carlos.getId()).header(AUTHORIZATION, tokenDe(carlos)))
                .andExpect(status().isOk());
    }
}
