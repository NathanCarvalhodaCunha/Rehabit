package com.rehabit.service;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.FisioterapeutaCreateDTO;
import com.rehabit.dto.FisioterapeutaPerfilDTO;
import com.rehabit.dto.FisioterapeutaResumoDTO;
import com.rehabit.dto.FisioterapeutaUpdateDTO;
import com.rehabit.email.ValidadorEmailService;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.security.PosseChecker;
import com.rehabit.model.Medicao;
import com.rehabit.model.Sessao;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FisioterapeutaService {

    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final ClinicaRepository clinicaRepository;
    private final PacienteRepository pacienteRepository;
    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final ValidadorEmailService validadorEmail;

    public FisioterapeutaService(FisioterapeutaRepository fisioterapeutaRepository,
                                  ClinicaRepository clinicaRepository,
                                  PacienteRepository pacienteRepository,
                                  SessaoRepository sessaoRepository,
                                  MedicaoRepository medicaoRepository,
                                  PasswordEncoder passwordEncoder,
                                  ValidadorEmailService validadorEmail) {
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.clinicaRepository = clinicaRepository;
        this.pacienteRepository = pacienteRepository;
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.validadorEmail = validadorEmail;
    }

    @Transactional
    public AuthResponseDTO cadastrar(FisioterapeutaCreateDTO dados, Integer usuarioId, String usuarioTipo) {
        PosseChecker.exigirClinicaDona(dados.getIdClinica(), usuarioId, usuarioTipo);
        if (!clinicaRepository.existsById(dados.getIdClinica())) {
            throw new AuthException("Instituição não encontrada.", HttpStatus.BAD_REQUEST);
        }

        // Mesma checagem do cadastro de instituição (formato, domínio
        // descartável e DNS). Aqui não cabe pedir o código por e-mail: quem
        // preenche a tela é a clínica, não a pessoa dona daquela caixa.
        String email = validadorEmail.validarENormalizar(dados.getEmail());

        if (fisioterapeutaRepository.existsByEmail(email)
                || clinicaRepository.existsByEmail(email)) {
            throw new AuthException("Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
        }

        if (fisioterapeutaRepository.existsByCoffito(dados.getCoffito())) {
            throw new AuthException("Este COFFITO já está cadastrado.", HttpStatus.CONFLICT);
        }

        Fisioterapeuta fisioterapeuta = new Fisioterapeuta();
        fisioterapeuta.setIdClinica(dados.getIdClinica());
        fisioterapeuta.setNome(dados.getNome());
        fisioterapeuta.setCoffito(dados.getCoffito());
        fisioterapeuta.setEmail(email);
        fisioterapeuta.setSenha(passwordEncoder.encode(dados.getSenha()));
        fisioterapeuta.setTelefone(vazioParaNulo(dados.getTelefone()));
        fisioterapeuta.setEspecialidade(vazioParaNulo(dados.getEspecialidade()));
        fisioterapeuta.setDescricao(vazioParaNulo(dados.getDescricao()));
        fisioterapeuta.setLocalidade(vazioParaNulo(dados.getLocalidade()));
        fisioterapeuta.setFoto(vazioParaNulo(dados.getFoto()));

        Fisioterapeuta salvo = fisioterapeutaRepository.save(fisioterapeuta);
        return new AuthResponseDTO(salvo.getId(), "FISIOTERAPEUTA", salvo.getNome(), salvo.getEmail(),
                salvo.getFoto(), salvo.isTutorialVisto());
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    public List<FisioterapeutaResumoDTO> listarPorClinica(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        PosseChecker.exigirClinicaDona(idClinica, usuarioId, usuarioTipo);
        return fisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(idClinica).stream()
                .map(f -> new FisioterapeutaResumoDTO(f.getId(), f.getNome(), f.getEspecialidade(), f.getFoto(),
                        pacienteRepository.countByIdFisioterapeutaAndStatus(f.getId(), "Ativo")))
                .collect(Collectors.toList());
    }

    public FisioterapeutaPerfilDTO buscarPerfil(Integer id, Integer usuarioId, String usuarioTipo) {
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(id)
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.NOT_FOUND));
        PosseChecker.exigirDonoOuClinicaDona(fisioterapeuta.getId(), fisioterapeuta.getIdClinica(), usuarioId, usuarioTipo);
        return paraPerfilDTO(fisioterapeuta);
    }

    @Transactional
    public FisioterapeutaPerfilDTO atualizar(Integer id, FisioterapeutaUpdateDTO dados, Integer usuarioId, String usuarioTipo) {
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(id)
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.NOT_FOUND));
        PosseChecker.exigirDonoOuClinicaDona(fisioterapeuta.getId(), fisioterapeuta.getIdClinica(), usuarioId, usuarioTipo);

        String email = validadorEmail.normalizar(dados.getEmail());
        if (!fisioterapeuta.getEmail().equalsIgnoreCase(email)) {
            email = validadorEmail.validarENormalizar(email);
            if (fisioterapeutaRepository.existsByEmail(email) || clinicaRepository.existsByEmail(email)) {
                throw new AuthException("Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
            }
        }

        if (dados.getNovaSenha() != null && !dados.getNovaSenha().isBlank()) {
            if (dados.getSenhaAtual() == null
                    || !passwordEncoder.matches(dados.getSenhaAtual(), fisioterapeuta.getSenha())) {
                throw new AuthException("Senha atual incorreta.", HttpStatus.BAD_REQUEST);
            }
            if (dados.getNovaSenha().length() < 6) {
                throw new AuthException("A nova senha deve ter ao menos 6 caracteres.", HttpStatus.BAD_REQUEST);
            }
            fisioterapeuta.setSenha(passwordEncoder.encode(dados.getNovaSenha()));
        } else if (dados.getSenhaAtual() != null && !dados.getSenhaAtual().isBlank()) {
            throw new AuthException("Informe a nova senha para trocar de senha.", HttpStatus.BAD_REQUEST);
        }

        fisioterapeuta.setNome(dados.getNome());
        fisioterapeuta.setEmail(email);
        fisioterapeuta.setTelefone(vazioParaNulo(dados.getTelefone()));
        fisioterapeuta.setEspecialidade(vazioParaNulo(dados.getEspecialidade()));
        fisioterapeuta.setLocalidade(vazioParaNulo(dados.getLocalidade()));
        fisioterapeuta.setDescricao(vazioParaNulo(dados.getDescricao()));
        if (dados.getFoto() != null) {
            fisioterapeuta.setFoto(dados.getFoto());
        }

        return paraPerfilDTO(fisioterapeutaRepository.save(fisioterapeuta));
    }

    @Transactional
    public void excluir(Integer id, Integer usuarioId, String usuarioTipo) {
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(id)
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.NOT_FOUND));
        PosseChecker.exigirClinicaDona(fisioterapeuta.getIdClinica(), usuarioId, usuarioTipo);

        long totalPacientes = pacienteRepository.countByIdFisioterapeuta(id);
        if (totalPacientes > 0) {
            throw new AuthException(
                    "Não é possível excluir: este profissional tem " + totalPacientes
                            + " paciente(s) cadastrado(s). Transfira ou remova os pacientes antes de excluir.",
                    HttpStatus.CONFLICT);
        }

        fisioterapeutaRepository.delete(fisioterapeuta);
    }

    @Transactional
    public void marcarTutorialVisto(Integer id) {
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(id)
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.NOT_FOUND));
        fisioterapeuta.setTutorialVisto(true);
        fisioterapeutaRepository.save(fisioterapeuta);
    }

    private String nomeDaClinica(Integer idClinica) {
        return idClinica == null ? null
                : clinicaRepository.findById(idClinica).map(c -> c.getNome()).orElse(null);
    }

    private FisioterapeutaPerfilDTO paraPerfilDTO(Fisioterapeuta f) {
        List<Sessao> sessoes = sessaoRepository.findByIdFisioterapeuta(f.getId());

        long sessoesEsteMes = sessoes.stream()
                .filter(s -> s.getDataSessao() != null && YearMonth.from(s.getDataSessao()).equals(YearMonth.now()))
                .count();

        List<BigDecimal> amplitudes = sessoes.stream()
                .map(s -> medicaoRepository.findByIdSessao(s.getId()))
                .filter(Objects::nonNull)
                .map(Medicao::getAmplitudeMedia)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Double amplitudeMediaGeral = amplitudes.isEmpty()
                ? null
                : amplitudes.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);

        long pacientesAtivos = pacienteRepository.countByIdFisioterapeutaAndStatus(f.getId(), "Ativo");

        FisioterapeutaPerfilDTO dto = new FisioterapeutaPerfilDTO(f.getId(), f.getNome(), f.getCoffito(),
                f.getEmail(), f.getTelefone(), f.getEspecialidade(), f.getLocalidade(), f.getDescricao(),
                f.getFoto(), f.getIdClinica(), pacientesAtivos, sessoesEsteMes, amplitudeMediaGeral);
        // O nome da instituição acompanha o perfil porque é ele que assina o
        // que sai para o paciente (lembrete no WhatsApp, relatório em PDF) —
        // quem atende é o profissional, mas quem se apresenta é a clínica.
        dto.setNomeClinica(nomeDaClinica(f.getIdClinica()));
        return dto;
    }
}
