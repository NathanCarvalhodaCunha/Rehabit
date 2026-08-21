package com.rehabit.service;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.LoginRequestDTO;
import com.rehabit.dto.RegisterRequestDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final ClinicaRepository clinicaRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(ClinicaRepository clinicaRepository,
                        FisioterapeutaRepository fisioterapeutaRepository,
                        PasswordEncoder passwordEncoder) {
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Autentica o usuário verificando o e-mail nas duas tabelas
     * (clínica e fisioterapeuta), já que a tela de login é única
     * para os dois tipos de conta.
     */
    public AuthResponseDTO login(LoginRequestDTO dados) {
        Optional<Clinica> clinica = clinicaRepository.findByEmail(dados.getEmail());
        if (clinica.isPresent()) {
            return autenticarClinica(clinica.get(), dados.getSenha());
        }

        Optional<Fisioterapeuta> fisioterapeuta = fisioterapeutaRepository.findByEmail(dados.getEmail());
        if (fisioterapeuta.isPresent()) {
            return autenticarFisioterapeuta(fisioterapeuta.get(), dados.getSenha());
        }

        throw new AuthException("E-mail ou senha inválidos.", HttpStatus.UNAUTHORIZED);
    }

    private AuthResponseDTO autenticarClinica(Clinica clinica, String senhaInformada) {
        if (!passwordEncoder.matches(senhaInformada, clinica.getSenha())) {
            throw new AuthException("E-mail ou senha inválidos.", HttpStatus.UNAUTHORIZED);
        }
        return new AuthResponseDTO(clinica.getId(), "CLINICA", clinica.getNome(), clinica.getEmail(), clinica.getFoto());
    }

    private AuthResponseDTO autenticarFisioterapeuta(Fisioterapeuta fisioterapeuta, String senhaInformada) {
        if (!passwordEncoder.matches(senhaInformada, fisioterapeuta.getSenha())) {
            throw new AuthException("E-mail ou senha inválidos.", HttpStatus.UNAUTHORIZED);
        }
        return new AuthResponseDTO(fisioterapeuta.getId(), "FISIOTERAPEUTA",
                fisioterapeuta.getNome(), fisioterapeuta.getEmail(), fisioterapeuta.getFoto());
    }

    /**
     * Cadastro via tela de registro (register.html). Apenas contas do tipo
     * "instituicao" podem ser criadas por aqui, pois tb02_fisioterapeuta
     * exige tb02_id_clinica (NOT NULL): um fisioterapeuta só pode ser
     * cadastrado vinculado a uma clínica já existente, fluxo que o
     * projeto já implementa em cadastrar-profissional.html.
     */
    @Transactional
    public AuthResponseDTO registrar(RegisterRequestDTO dados) {
        if ("profissional".equalsIgnoreCase(dados.getTipo())) {
            throw new AuthException(
                    "Contas de profissional devem ser cadastradas por uma instituição já registrada.",
                    HttpStatus.BAD_REQUEST);
        }

        if (clinicaRepository.existsByEmail(dados.getEmail())
                || fisioterapeutaRepository.existsByEmail(dados.getEmail())) {
            throw new AuthException("Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
        }

        if (clinicaRepository.existsByCnpj(dados.getCnpj())) {
            throw new AuthException("Este CNPJ já está cadastrado.", HttpStatus.CONFLICT);
        }

        Clinica clinica = new Clinica();
        clinica.setNome(dados.getNome());
        clinica.setEmail(dados.getEmail());
        clinica.setSenha(passwordEncoder.encode(dados.getSenha()));
        clinica.setCnpj(dados.getCnpj().trim());
        clinica.setTelefone(vazioParaNulo(dados.getTelefone()));
        clinica.setEndereco(vazioParaNulo(dados.getEndereco()));
        clinica.setSubtitulo(vazioParaNulo(dados.getSubtitulo()));
        clinica.setDescricao(vazioParaNulo(dados.getDescricao()));
        clinica.setFoto(vazioParaNulo(dados.getFoto()));

        Clinica salva = clinicaRepository.save(clinica);
        return new AuthResponseDTO(salva.getId(), "CLINICA", salva.getNome(), salva.getEmail(), salva.getFoto());
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
