package com.rehabit.service;

import com.rehabit.dto.ResumoExclusaoContaDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.AgendamentoRepository;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.CodigoPareamentoRepository;
import com.rehabit.repository.ConfiguracaoRepository;
import com.rehabit.repository.DispositivoRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.GoniometroRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.NotificacaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.RecuperacaoSenhaRepository;
import com.rehabit.repository.SessaoRepository;
import com.rehabit.repository.VerificacaoEmailRepository;
import com.rehabit.security.PosseChecker;
import com.rehabit.storage.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A clínica exclui a própria conta, e tudo que pertence a ela vai junto — de
 * verdade, sem exclusão lógica: não há histórico a preservar para ninguém
 * quando a clínica inteira está saindo.
 *
 * As ligações entre as tabelas são ids soltos, sem cascade do JPA, e as FKs
 * do Rehabit.sql não têm ON DELETE. Por isso os filhos saem antes dos pais,
 * em ordem, dentro de uma transação só: se um passo falhar, nada é apagado.
 * Funciona igual no H2, no PostgreSQL e num MySQL criado pelo script.
 *
 * O teste ExclusaoDeContaDaClinicaTest confere que nenhuma tabela fica com
 * linha órfã — e quebra se surgir uma entidade nova que ninguém incluiu aqui.
 */
@Service
public class ExclusaoDeContaService {

    private final ClinicaRepository clinicaRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final PacienteRepository pacienteRepository;
    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ConfiguracaoRepository configuracaoRepository;
    private final RecuperacaoSenhaRepository recuperacaoSenhaRepository;
    private final VerificacaoEmailRepository verificacaoEmailRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final DispositivoRepository dispositivoRepository;
    private final GoniometroRepository goniometroRepository;
    private final CodigoPareamentoRepository codigoPareamentoRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public ExclusaoDeContaService(ClinicaRepository clinicaRepository,
                                  FisioterapeutaRepository fisioterapeutaRepository,
                                  PacienteRepository pacienteRepository,
                                  SessaoRepository sessaoRepository,
                                  MedicaoRepository medicaoRepository,
                                  AgendamentoRepository agendamentoRepository,
                                  ConfiguracaoRepository configuracaoRepository,
                                  RecuperacaoSenhaRepository recuperacaoSenhaRepository,
                                  VerificacaoEmailRepository verificacaoEmailRepository,
                                  NotificacaoRepository notificacaoRepository,
                                  DispositivoRepository dispositivoRepository,
                                  GoniometroRepository goniometroRepository,
                                  CodigoPareamentoRepository codigoPareamentoRepository,
                                  PasswordEncoder passwordEncoder,
                                  FileStorageService fileStorageService) {
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.pacienteRepository = pacienteRepository;
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.recuperacaoSenhaRepository = recuperacaoSenhaRepository;
        this.verificacaoEmailRepository = verificacaoEmailRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.goniometroRepository = goniometroRepository;
        this.codigoPareamentoRepository = codigoPareamentoRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
    }

    /** O que vai sumir, para a clínica ver antes de confirmar. */
    public ResumoExclusaoContaDTO resumo(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        PosseChecker.exigirClinicaDona(idClinica, usuarioId, usuarioTipo);
        List<Integer> idsPacientes = idsDe(pacienteRepository.findByIdClinica(idClinica));

        return new ResumoExclusaoContaDTO(
                fisioterapeutaRepository.countByIdClinicaAndExcluidoEmIsNull(idClinica),
                idsPacientes.size(),
                idsPacientes.isEmpty() ? 0 : sessaoRepository.countByIdPacienteIn(idsPacientes),
                idsPacientes.isEmpty() ? 0 : agendamentoRepository.countByIdPacienteIn(idsPacientes),
                dispositivoRepository.countByIdClinica(idClinica));
    }

    @Transactional
    public void excluir(Integer idClinica, String senha, Integer usuarioId, String usuarioTipo) {
        PosseChecker.exigirClinicaDona(idClinica, usuarioId, usuarioTipo);
        Clinica clinica = clinicaRepository.findById(idClinica)
                .orElseThrow(() -> new AuthException("Instituição não encontrada.", HttpStatus.NOT_FOUND));
        if (senha == null || !passwordEncoder.matches(senha, clinica.getSenha())) {
            throw new AuthException("Senha incorreta.", HttpStatus.FORBIDDEN);
        }

        // Inclui os profissionais excluídos: a linha deles ainda existe.
        List<Fisioterapeuta> profissionais = fisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(idClinica);
        List<Paciente> pacientes = pacienteRepository.findByIdClinica(idClinica);
        List<Integer> idsProfissionais = idsDosProfissionais(profissionais);
        List<Integer> idsPacientes = idsDe(pacientes);

        // Lidos antes de apagar: depois não há mais de onde tirá-los.
        List<String> fotos = Stream.of(
                        Stream.of(clinica.getFoto()),
                        profissionais.stream().map(Fisioterapeuta::getFoto),
                        pacientes.stream().map(Paciente::getFoto))
                .flatMap(s -> s)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<String> emails = new ArrayList<>();
        emails.add(clinica.getEmail());
        profissionais.forEach(f -> emails.add(f.getEmail()));

        // 1. Prontuário: medições, sessões e agenda, depois os pacientes.
        if (!idsPacientes.isEmpty()) {
            List<Integer> idsSessoes = sessaoRepository.findByIdPacienteIn(idsPacientes).stream()
                    .map(Sessao::getId)
                    .collect(Collectors.toList());
            if (!idsSessoes.isEmpty()) {
                medicaoRepository.deleteByIdSessaoIn(idsSessoes);
            }
            sessaoRepository.deleteByIdPacienteIn(idsPacientes);
            agendamentoRepository.deleteByIdPacienteIn(idsPacientes);
        }
        pacienteRepository.deleteByIdClinica(idClinica);

        // 2. Configurações da clínica e de cada profissional.
        configuracaoRepository.deleteByTipoUsuarioAndIdUsuarioIn("CLINICA", List.of(idClinica));
        if (!idsProfissionais.isEmpty()) {
            configuracaoRepository.deleteByTipoUsuarioAndIdUsuarioIn("FISIOTERAPEUTA", idsProfissionais);
        }

        // 3. Um link de "redefinir senha" ou um código de cadastro pendente
        //    não pode sobreviver à conta.
        emails.forEach(email -> {
            recuperacaoSenhaRepository.deleteByEmail(email);
            verificacaoEmailRepository.deleteByEmail(email);
        });

        // 4. Profissionais, aparelhos e o que mais pende da clínica; ela por último.
        fisioterapeutaRepository.deleteByIdClinica(idClinica);
        notificacaoRepository.deleteByIdClinica(idClinica);
        dispositivoRepository.deleteByIdClinica(idClinica);
        goniometroRepository.deleteByIdClinica(idClinica);
        codigoPareamentoRepository.deleteByIdClinica(idClinica);
        clinicaRepository.delete(clinica);

        apagarFotosDepoisDoCommit(fotos);
    }

    /**
     * As fotos saem só depois que o banco confirma, e sem poder desfazê-lo.
     * A parte que precisa ser atômica é o banco: uma foto esquecida é limpeza
     * recuperável, uma conta apagada pela metade não é. E apagar a foto antes
     * do commit deixaria uma conta intacta sem foto se o banco falhasse.
     */
    private void apagarFotosDepoisDoCommit(List<String> fotos) {
        if (fotos.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fotos.forEach(fileStorageService::excluir);
            }
        });
    }

    private static List<Integer> idsDe(List<Paciente> pacientes) {
        return pacientes.stream().map(Paciente::getId).collect(Collectors.toList());
    }

    private static List<Integer> idsDosProfissionais(List<Fisioterapeuta> profissionais) {
        return profissionais.stream().map(Fisioterapeuta::getId).collect(Collectors.toList());
    }
}
