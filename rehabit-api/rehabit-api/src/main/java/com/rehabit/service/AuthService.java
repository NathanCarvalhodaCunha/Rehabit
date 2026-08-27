package com.rehabit.service;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.LoginRequestDTO;
import com.rehabit.dto.RedefinirSenhaRequestDTO;
import com.rehabit.dto.RegisterRequestDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.security.JwtService;
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
    private final JwtService jwtService;

    public AuthService(ClinicaRepository clinicaRepository,
                        FisioterapeutaRepository fisioterapeutaRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        String token = jwtService.gerarToken(clinica.getId(), "CLINICA");
        AuthResponseDTO resposta = new AuthResponseDTO(clinica.getId(), "CLINICA", clinica.getNome(),
                clinica.getEmail(), clinica.getFoto(), clinica.isTutorialVisto(), token);
        resposta.setNomeClinica(clinica.getNome());
        return resposta;
    }

    private AuthResponseDTO autenticarFisioterapeuta(Fisioterapeuta fisioterapeuta, String senhaInformada) {
        if (!passwordEncoder.matches(senhaInformada, fisioterapeuta.getSenha())) {
            throw new AuthException("E-mail ou senha inválidos.", HttpStatus.UNAUTHORIZED);
        }
        String token = jwtService.gerarToken(fisioterapeuta.getId(), "FISIOTERAPEUTA");
        AuthResponseDTO resposta = new AuthResponseDTO(fisioterapeuta.getId(), "FISIOTERAPEUTA",
                fisioterapeuta.getNome(), fisioterapeuta.getEmail(), fisioterapeuta.getFoto(),
                fisioterapeuta.isTutorialVisto(), token);
        resposta.setNomeClinica(clinicaRepository.findById(fisioterapeuta.getIdClinica())
                .map(Clinica::getNome).orElse(null));
        return resposta;
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
        return new AuthResponseDTO(salva.getId(), "CLINICA", salva.getNome(), salva.getEmail(),
                salva.getFoto(), salva.isTutorialVisto());
    }

    /**
     * Redefinição direta de senha (sem e-mail): identifica a conta pelo
     * e-mail e confirma a posse dela através do CNPJ (clínica) ou COFFITO
     * (fisioterapeuta), únicos por conta e não expostos publicamente.
     * A mensagem de erro é sempre a mesma para não revelar qual dado
     * (e-mail ou documento) estava incorreto.
     */
    @Transactional
    public void redefinirSenha(RedefinirSenhaRequestDTO dados) {
        String documentoInformado = dados.getDocumento().trim();
        AuthException dadosInvalidos = new AuthException(
                "Não foi possível verificar seus dados. Confira o e-mail e o CNPJ/COFFITO informados.",
                HttpStatus.BAD_REQUEST);

        Optional<Clinica> clinica = clinicaRepository.findByEmail(dados.getEmail());
        if (clinica.isPresent()) {
            Clinica c = clinica.get();
            if (c.getCnpj() == null || !documentoInformado.equalsIgnoreCase(c.getCnpj().trim())) {
                throw dadosInvalidos;
            }
            c.setSenha(passwordEncoder.encode(dados.getNovaSenha()));
            clinicaRepository.save(c);
            return;
        }

        Optional<Fisioterapeuta> fisioterapeuta = fisioterapeutaRepository.findByEmail(dados.getEmail());
        if (fisioterapeuta.isPresent()) {
            Fisioterapeuta f = fisioterapeuta.get();
            if (f.getCoffito() == null || !documentoInformado.equalsIgnoreCase(f.getCoffito().trim())) {
                throw dadosInvalidos;
            }
            f.setSenha(passwordEncoder.encode(dados.getNovaSenha()));
            fisioterapeutaRepository.save(f);
            return;
        }

        throw dadosInvalidos;
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
