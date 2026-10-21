package com.rehabit.demo;

import com.rehabit.config.RelogioConfig;
import com.rehabit.demo.GeradorDeClinica.DadosDaClinica;
import com.rehabit.demo.GeradorDeClinica.PacienteFicticio;
import com.rehabit.demo.GeradorDeClinica.Profissional;
import com.rehabit.demo.GeradorDeClinica.Roteiro;
import com.rehabit.model.Agendamento;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Notificacao;
import com.rehabit.model.Paciente;
import com.rehabit.model.SenhaAplicada;
import com.rehabit.model.Sessao;
import com.rehabit.repository.AgendamentoRepository;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.NotificacaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.RecuperacaoSenhaRepository;
import com.rehabit.repository.SenhaAplicadaRepository;
import com.rehabit.repository.SessaoRepository;
import com.rehabit.security.Limite;
import com.rehabit.security.LimiteDeTentativas;
import com.rehabit.service.AgendamentoService;
import com.rehabit.storage.EnvioDeFoto;
import com.rehabit.storage.FileStorageService;
import com.rehabit.storage.FotosEmUso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cuida de uma clínica de roteiro que aceita alterações — a conta da
 * apresentação — sem nunca apagar nada:
 *
 * - fotos: põe as do roteiro onde falta foto; a que a equipe trocou pela tela
 *   fica;
 * - senha: aplica a senha configurada às contas do roteiro quando a
 *   configuração muda (a troca derruba as sessões abertas, como qualquer
 *   troca); uma senha trocada pela tela fica até a configuração mudar;
 * - atendimento: a clínica segue atendendo. A consulta de um paciente do
 *   roteiro que passou sem ninguém mexer nela (ainda marcada) vira realizada,
 *   com a sessão, a medição e o aviso no sino, e a agenda de quem está em
 *   tratamento anda para ter sempre "semanasDeAgenda" pela frente. Assim o
 *   histórico chega até ontem em qualquer dia, sem recriar a conta.
 *
 * O que a equipe marcou, cancelou, remarcou ou registrou pela tela fica como
 * está, e paciente cadastrado por ela não entra nisso. A demonstração não
 * precisa de nada disso: ninguém mexe nela, então ela é refeita inteira
 * (GeradorDeClinica.refazerSeVencida); aqui, refazer apagaria o que a equipe
 * fez e o goniômetro pareado.
 */
@Component
public class ManutencaoDaClinica {

    private static final Logger log = LoggerFactory.getLogger(ManutencaoDaClinica.class);

    /** Avisos no sino por rodada; como na montagem, só os mais novos ficam não lidos. */
    private static final int AVISOS = 8;
    private static final int AVISOS_NAO_LIDOS = 3;

    private final ClinicaRepository clinicas;
    private final FisioterapeutaRepository fisioterapeutas;
    private final PacienteRepository pacientes;
    private final SessaoRepository sessoes;
    private final MedicaoRepository medicoes;
    private final AgendamentoRepository agendamentos;
    private final AgendamentoService agendamentoService;
    private final NotificacaoRepository notificacoes;
    private final RecuperacaoSenhaRepository recuperacoes;
    private final SenhaAplicadaRepository senhasAplicadas;
    private final PasswordEncoder passwordEncoder;
    private final LimiteDeTentativas limites;
    private final EnvioDeFoto envioDeFoto;
    private final FotosEmUso fotosEmUso;
    private final FileStorageService armazenamento;
    private final Clock relogio;

    public ManutencaoDaClinica(ClinicaRepository clinicas, FisioterapeutaRepository fisioterapeutas,
                               PacienteRepository pacientes, SessaoRepository sessoes, MedicaoRepository medicoes,
                               AgendamentoRepository agendamentos, AgendamentoService agendamentoService,
                               NotificacaoRepository notificacoes,
                               RecuperacaoSenhaRepository recuperacoes, SenhaAplicadaRepository senhasAplicadas,
                               PasswordEncoder passwordEncoder,
                               LimiteDeTentativas limites, EnvioDeFoto envioDeFoto, FotosEmUso fotosEmUso,
                               FileStorageService armazenamento, Clock relogio) {
        this.clinicas = clinicas;
        this.fisioterapeutas = fisioterapeutas;
        this.pacientes = pacientes;
        this.sessoes = sessoes;
        this.medicoes = medicoes;
        this.agendamentos = agendamentos;
        this.agendamentoService = agendamentoService;
        this.notificacoes = notificacoes;
        this.recuperacoes = recuperacoes;
        this.senhasAplicadas = senhasAplicadas;
        this.passwordEncoder = passwordEncoder;
        this.limites = limites;
        this.envioDeFoto = envioDeFoto;
        this.fotosEmUso = fotosEmUso;
        this.armazenamento = armazenamento;
        this.relogio = relogio;
    }

    /**
     * A clínica do roteiro é a que tem o CNPJ dele (coluna única). Não pelo
     * e-mail: se a equipe trocar o da clínica pela tela, outra clínica pode
     * adotar o antigo e passaria a receber a senha e as fotos.
     */
    private Clinica clinicaDo(Roteiro roteiro) {
        return clinicas.findByCnpj(roteiro.clinica().cnpj()).orElse(null);
    }

    /**
     * Um profissional do roteiro: pelo e-mail, e só se for da clínica do
     * roteiro e não tiver sido excluído. Excluído pela clínica, ele deixa o
     * e-mail livre, e quem o adotar em outra clínica não é dele.
     */
    private Fisioterapeuta profissionalDo(Clinica clinica, Profissional p) {
        return fisioterapeutas.findByEmail(p.email())
                .filter(f -> clinica.getId().equals(f.getIdClinica()) && !f.isExcluido())
                .orElse(null);
    }

    // ---------------------------------------------------------------- fotos

    /**
     * Põe as fotos do roteiro na clínica e em cada profissional, procurados
     * pelo e-mail — só onde não há foto, ou onde ela aponta para um arquivo
     * que não existe mais (o disco de um servidor recriado). Fica fora de
     * transação de propósito: cada envio é registrado sozinho e, se a gravação
     * no cadastro não acontecer, a LimpezaDeEnvios apaga o arquivo que sobrou.
     *
     * @return quantas fotos foram postas
     */
    public int completarFotos(Roteiro roteiro) {
        int postas = 0;
        DadosDaClinica dados = roteiro.clinica();
        if (dados.foto() != null) {
            Clinica clinica = clinicaDo(roteiro);
            if (clinica != null && semFoto(clinica.getFoto())) {
                String url = enviar(dados.foto());
                if (url != null) {
                    String anterior = clinica.getFoto();
                    clinica.setFoto(url);
                    clinicas.save(clinica);
                    descartar(anterior);
                    postas++;
                }
            }
        }
        Clinica daClinica = clinicaDo(roteiro);
        for (Profissional p : roteiro.profissionais()) {
            if (p.foto() == null || daClinica == null) continue;
            Fisioterapeuta f = profissionalDo(daClinica, p);
            if (f == null || !semFoto(f.getFoto())) continue;
            String url = enviar(p.foto());
            if (url != null) {
                String anterior = f.getFoto();
                f.setFoto(url);
                fisioterapeutas.save(f);
                descartar(anterior);
                postas++;
            }
        }
        if (postas > 0) log.info("{}: {} foto(s) posta(s).", dados.nome(), postas);
        return postas;
    }

    private boolean semFoto(String foto) {
        return foto == null || foto.isBlank() || !armazenamento.guardou(foto);
    }

    /** Envia a imagem do jar pelo mesmo caminho das fotos do site; null se não deu. */
    private String enviar(String recurso) {
        try (InputStream entrada = new ClassPathResource(recurso).getInputStream()) {
            // aceitar confere que a URL é mesmo deste armazenamento.
            return fotosEmUso.aceitar(envioDeFoto.salvar(entrada.readAllBytes()), null);
        } catch (IOException | RuntimeException e) {
            log.warn("Foto {} não enviada: {}", recurso, e.getMessage());
            return null;
        }
    }

    private void descartar(String anterior) {
        if (anterior != null && !anterior.isBlank()) fotosEmUso.descartarDepoisDoCommit(anterior);
    }

    // ---------------------------------------------------------------- senha

    /**
     * Aplica a senha configurada à clínica e aos profissionais do roteiro —
     * mas só quando a configuração muda (ou na primeira vez). Uma senha
     * trocada pela tela, às vezes justamente porque a da variável vazou, fica
     * valendo nas subidas seguintes; para aplicar de novo, muda-se a variável.
     *
     * Entre as contas, só troca a de quem está com outra senha: a troca derruba
     * as sessões abertas daquela conta. Como na redefinição por e-mail, zera o
     * limite de tentativas de login e invalida links de recuperação pendentes.
     *
     * @return quantas contas tiveram a senha trocada
     */
    @Transactional
    public int alinharSenhas(Roteiro roteiro, String senha) {
        Clinica clinica = clinicaDo(roteiro);
        if (clinica == null) return 0;
        SenhaAplicada ultima = senhasAplicadas.findById(roteiro.clinica().cnpj()).orElse(null);
        if (ultima != null && passwordEncoder.matches(senha, ultima.getImpressao())) return 0;

        int trocadas = 0;
        if (!passwordEncoder.matches(senha, clinica.getSenha())) {
            clinica.trocarSenha(passwordEncoder.encode(senha));
            clinicas.save(clinica);
            depoisDaTroca(clinica.getEmail());
            trocadas++;
        }
        for (Profissional p : roteiro.profissionais()) {
            Fisioterapeuta f = profissionalDo(clinica, p);
            if (f == null || passwordEncoder.matches(senha, f.getSenha())) continue;
            f.trocarSenha(passwordEncoder.encode(senha));
            fisioterapeutas.save(f);
            depoisDaTroca(f.getEmail());
            trocadas++;
        }
        senhasAplicadas.save(new SenhaAplicada(roteiro.clinica().cnpj(), passwordEncoder.encode(senha),
                relogio.instant()));
        if (trocadas > 0) {
            log.info("{}: senha alinhada com a configuração em {} conta(s).", roteiro.clinica().nome(), trocadas);
        }
        return trocadas;
    }

    private void depoisDaTroca(String email) {
        limites.zerar(Limite.LOGIN_POR_CONTA, email);
        recuperacoes.deleteByEmail(email);
    }

    // ----------------------------------------------------------- atendimento

    /**
     * A clínica segue atendendo até hoje (veja a descrição da classe). Pode
     * rodar quantas vezes for: o que já foi atendido não é atendido de novo,
     * e a agenda só cresce depois da última consulta de cada paciente. Depois
     * de dias sem rodar, põe tudo em dia de uma vez.
     *
     * @return quantas sessões foram registradas
     */
    @Transactional
    public int seguirAtendendo(Roteiro roteiro) {
        Clinica clinica = clinicaDo(roteiro);
        if (clinica == null) return 0;
        LocalDate hoje = LocalDate.now(relogio.withZone(RelogioConfig.FUSO));
        Map<String, PacienteFicticio> doRoteiro = roteiro.pacientes().stream()
                .collect(Collectors.toMap(PacienteFicticio::cpf, Function.identity()));

        List<Sessao> registradas = new ArrayList<>();
        for (Paciente paciente : pacientes.findByIdClinica(clinica.getId())) {
            PacienteFicticio ficticio = doRoteiro.get(paciente.getCpf());
            // Paciente da equipe, de alta, inativo ou sem profissional: fica como está.
            if (ficticio == null || !"Ativo".equals(paciente.getStatus())) continue;
            Fisioterapeuta responsavel = paciente.getIdFisioterapeuta() == null ? null
                    : fisioterapeutas.findById(paciente.getIdFisioterapeuta()).orElse(null);
            if (responsavel == null || responsavel.isExcluido()) continue;

            List<Agendamento> agenda = new ArrayList<>(
                    agendamentos.findByIdPacienteOrderByDataAgendamentoAscHoraAgendamentoAsc(paciente.getId()));
            estenderAgenda(ficticio, paciente, roteiro.pacientes().indexOf(ficticio), agenda, hoje,
                    roteiro.semanasDeAgenda());
            registradas.addAll(atenderOQuePassou(ficticio, paciente, agenda, hoje));
        }
        avisar(clinica, registradas);
        if (!registradas.isEmpty()) {
            log.info("{}: {} sessão(ões) registrada(s) até {}.", roteiro.clinica().nome(), registradas.size(),
                    hoje.minusDays(1));
        }
        return registradas.size();
    }

    /**
     * Da última consulta do paciente até "semanas" à frente, nos dias e no
     * horário dele. Pula o dia em que o profissional já tem alguém nesse
     * horário (uma consulta que a equipe marcou). Depois de dias sem rodar,
     * também cria os dias que ficaram para trás — e atenderOQuePassou os
     * atende em seguida.
     */
    private void estenderAgenda(PacienteFicticio ficticio, Paciente paciente, int indice, List<Agendamento> agenda,
                                LocalDate hoje, int semanas) {
        if (agenda.isEmpty()) return; // sem nenhuma consulta: a equipe tirou o paciente da agenda
        LocalDate ultima = agenda.get(agenda.size() - 1).getDataAgendamento();
        LocalDate ate = hoje.plusWeeks(semanas);
        int n = agenda.size();
        List<Agendamento> novas = new ArrayList<>();
        for (LocalDate dia = ultima.plusDays(1); !dia.isAfter(ate); dia = dia.plusDays(1)) {
            if (!ficticio.dias().contains(dia.getDayOfWeek())) continue;
            if (horarioOcupado(paciente.getIdFisioterapeuta(), dia, ficticio.horario())) continue;
            Agendamento a = GeradorDeClinica.novoAgendamento(paciente, dia, ficticio.horario(),
                    GeradorDeClinica.OBSERVACOES_AGENDA.get((indice + n) % GeradorDeClinica.OBSERVACOES_AGENDA.size()));
            novas.add(agendamentos.save(a));
            n++;
        }
        agenda.addAll(novas);
    }

    /**
     * A mesma regra da tela de agenda (consultas que se sobrepõem na duração
     * configurada) e, mesmo com o aviso de conflito desligado, nunca duas
     * consultas no mesmo horário.
     */
    private boolean horarioOcupado(Integer idFisioterapeuta, LocalDate dia, LocalTime hora) {
        return agendamentoService.conflitaComAgenda(idFisioterapeuta, dia, hora)
                || agendamentos.findByIdFisioterapeutaAndDataAgendamento(idFisioterapeuta, dia).stream()
                        .anyMatch(a -> hora.equals(a.getHoraAgendamento()));
    }

    /**
     * Cada consulta que passou ainda marcada (AGENDADA ou REMARCADA) vira
     * REALIZADA. Se a equipe já registrou sessão do paciente naquele dia, é
     * só isso; senão, entra a sessão que a consulta teria gerado.
     */
    private List<Sessao> atenderOQuePassou(PacienteFicticio ficticio, Paciente paciente, List<Agendamento> agenda,
                                           LocalDate hoje) {
        List<Sessao> doPaciente = sessoes.findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(paciente.getId());
        Set<LocalDate> diasComSessao = doPaciente.stream().map(Sessao::getDataSessao)
                .collect(Collectors.toCollection(HashSet::new));
        int k = doPaciente.size();
        // As duas medições mais recentes, da mais nova para a mais antiga.
        List<BigDecimal> ultimas = new ArrayList<>(doPaciente.stream().limit(2)
                .map(s -> medicoes.findByIdSessao(s.getId()))
                .filter(m -> m != null && m.getAmplitudeMedia() != null)
                .map(Medicao::getAmplitudeMedia)
                .toList());
        List<Sessao> registradas = new ArrayList<>();
        for (Agendamento a : agenda) {
            if (!a.getDataAgendamento().isBefore(hoje)) break; // a de hoje ainda pode ser marcada na tela
            if (!GeradorDeClinica.STATUS_A_VIR.contains(a.getStatus())) continue;
            a.setStatus("REALIZADA");
            agendamentos.save(a);
            if (!diasComSessao.add(a.getDataAgendamento())) continue;
            registradas.add(registrarSessao(ficticio, paciente, a, k++, ultimas));
        }
        return registradas;
    }

    /**
     * A sessão de uma consulta depois do fim do roteiro. O roteiro leva o
     * paciente de ampInicial a ampFinal; daqui em diante ele segue ganhando
     * pouco, cada vez menos, em direção à meta, com a oscilação normal de um
     * dia para o outro — nenhuma evolução real é uma reta. A dor fica no
     * patamar final, e o registro sai ao fim da consulta, como na montagem.
     */
    private Sessao registrarSessao(PacienteFicticio ficticio, Paciente paciente, Agendamento consulta, int k,
                                   List<BigDecimal> ultimas) {
        int alem = Math.max(1, k - ficticio.sessoes() + 1);
        double rumoAMeta = Math.max(0, ficticio.meta() - ficticio.ampFinal()) * 0.6 * (1 - Math.exp(-alem / 10.0));
        BigDecimal amplitude = BigDecimal.valueOf(Math.max(0, ficticio.ampFinal() + rumoAMeta + Math.sin(k * 1.7) * 1.8))
                .setScale(1, RoundingMode.HALF_UP);
        // Duas quedas seguidas acendem o alerta "Regressão na amplitude" na
        // tela da clínica: a oscilação do dia a dia não pode inventar uma piora.
        if (ultimas.size() == 2 && ultimas.get(0).compareTo(ultimas.get(1)) < 0
                && amplitude.compareTo(ultimas.get(0)) < 0) {
            amplitude = ultimas.get(0).add(new BigDecimal("0.4")).setScale(1, RoundingMode.HALF_UP);
        }
        ultimas.add(0, amplitude);
        if (ultimas.size() > 2) ultimas.remove(2);
        int dor = Math.max(0, Math.min(10, ficticio.dorFinal() + (k % 5 == 3 ? 1 : 0)));
        int duracao = ficticio.duracao() + (k % 3 == 1 ? 5 : k % 3 == 2 ? -5 : 0);
        LocalTime registro = consulta.getHoraAgendamento().plusMinutes(duracao - 6 + (k * 7) % 9);

        Sessao s = new Sessao();
        s.setIdPaciente(paciente.getId());
        s.setIdFisioterapeuta(consulta.getIdFisioterapeuta());
        s.setDataSessao(consulta.getDataAgendamento());
        s.setHoraSessao(registro);
        s.setDuracao(duracao);
        s.setDor(dor);
        s.setObservacoes(GeradorDeClinica.prontuario(k, 1.0));
        Sessao salva = sessoes.save(s);

        Medicao m = new Medicao();
        m.setIdSessao(salva.getId());
        m.setAmplitudeMedia(amplitude);
        m.setDataMedicao(salva.getDataSessao());
        m.setHoraMedicao(registro);
        medicoes.save(m);
        return salva;
    }

    /** O sino da clínica com as sessões novas, como o registro de verdade faz. */
    private void avisar(Clinica clinica, List<Sessao> registradas) {
        Map<Integer, String> nomes = new HashMap<>();
        List<Sessao> maisNovas = registradas.stream()
                .sorted((a, b) -> LocalDateTime.of(b.getDataSessao(), b.getHoraSessao())
                        .compareTo(LocalDateTime.of(a.getDataSessao(), a.getHoraSessao())))
                .limit(AVISOS)
                .toList();
        for (int i = 0; i < maisNovas.size(); i++) {
            Sessao s = maisNovas.get(i);
            String nome = nomes.computeIfAbsent(s.getIdPaciente(),
                    id -> pacientes.findById(id).map(Paciente::getNome).orElse("paciente"));
            Notificacao n = new Notificacao();
            n.setIdClinica(clinica.getId());
            n.setTipo("NOVA_SESSAO");
            n.setMensagem("Nova sessão registrada para " + nome);
            n.setLida(i >= AVISOS_NAO_LIDOS);
            n.setCriadaEm(LocalDateTime.of(s.getDataSessao(), s.getHoraSessao()));
            notificacoes.save(n);
        }
    }
}
