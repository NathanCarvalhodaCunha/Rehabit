package com.rehabit.service;

import com.rehabit.dto.ClinicaPerfilDTO;
import com.rehabit.dto.ClinicaUpdateDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
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
public class ClinicaService {

    private final ClinicaRepository clinicaRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final PacienteRepository pacienteRepository;
    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;
    private final PasswordEncoder passwordEncoder;

    public ClinicaService(ClinicaRepository clinicaRepository,
                           FisioterapeutaRepository fisioterapeutaRepository,
                           PacienteRepository pacienteRepository,
                           SessaoRepository sessaoRepository,
                           MedicaoRepository medicaoRepository,
                           PasswordEncoder passwordEncoder) {
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.pacienteRepository = pacienteRepository;
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public ClinicaPerfilDTO buscarPerfil(Integer id) {
        Clinica clinica = clinicaRepository.findById(id)
                .orElseThrow(() -> new AuthException("Instituição não encontrada.", HttpStatus.NOT_FOUND));
        return paraDTO(clinica);
    }

    @Transactional
    public ClinicaPerfilDTO atualizar(Integer id, ClinicaUpdateDTO dados) {
        Clinica clinica = clinicaRepository.findById(id)
                .orElseThrow(() -> new AuthException("Instituição não encontrada.", HttpStatus.NOT_FOUND));

        if (!clinica.getEmail().equalsIgnoreCase(dados.getEmail())
                && (clinicaRepository.existsByEmail(dados.getEmail())
                || fisioterapeutaRepository.existsByEmail(dados.getEmail()))) {
            throw new AuthException("Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
        }
        if (!clinica.getCnpj().equals(dados.getCnpj())
                && clinicaRepository.existsByCnpj(dados.getCnpj())) {
            throw new AuthException("Este CNPJ já está cadastrado.", HttpStatus.CONFLICT);
        }

        if (dados.getNovaSenha() != null && !dados.getNovaSenha().isBlank()) {
            if (dados.getSenhaAtual() == null || !passwordEncoder.matches(dados.getSenhaAtual(), clinica.getSenha())) {
                throw new AuthException("Senha atual incorreta.", HttpStatus.BAD_REQUEST);
            }
            if (dados.getNovaSenha().length() < 6) {
                throw new AuthException("A nova senha deve ter ao menos 6 caracteres.", HttpStatus.BAD_REQUEST);
            }
            clinica.setSenha(passwordEncoder.encode(dados.getNovaSenha()));
        } else if (dados.getSenhaAtual() != null && !dados.getSenhaAtual().isBlank()) {
            throw new AuthException("Informe a nova senha para trocar de senha.", HttpStatus.BAD_REQUEST);
        }

        clinica.setNome(dados.getNome());
        clinica.setCnpj(dados.getCnpj());
        clinica.setEmail(dados.getEmail());
        clinica.setTelefone(vazioParaNulo(dados.getTelefone()));
        clinica.setEndereco(vazioParaNulo(dados.getEndereco()));
        clinica.setSubtitulo(vazioParaNulo(dados.getSubtitulo()));
        clinica.setDescricao(vazioParaNulo(dados.getDescricao()));
        if (dados.getFoto() != null) {
            clinica.setFoto(dados.getFoto());
        }

        return paraDTO(clinicaRepository.save(clinica));
    }

    private ClinicaPerfilDTO paraDTO(Clinica clinica) {
        List<Fisioterapeuta> fisioterapeutas = fisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(clinica.getId());
        List<Integer> idsFisioterapeutas = fisioterapeutas.stream().map(Fisioterapeuta::getId).collect(Collectors.toList());
        List<Sessao> sessoes = idsFisioterapeutas.isEmpty()
                ? List.of()
                : sessaoRepository.findByIdFisioterapeutaIn(idsFisioterapeutas);

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

        return new ClinicaPerfilDTO(clinica.getId(), clinica.getNome(), clinica.getCnpj(), clinica.getEmail(),
                clinica.getTelefone(), clinica.getEndereco(), clinica.getSubtitulo(), clinica.getDescricao(),
                clinica.getFoto(), fisioterapeutas.size(), pacienteRepository.countByIdClinica(clinica.getId()),
                sessoesEsteMes, amplitudeMediaGeral);
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
