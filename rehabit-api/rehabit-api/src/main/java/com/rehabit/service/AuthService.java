package com.rehabit.service;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.LoginRequestDTO;
import com.rehabit.dto.RegisterRequestDTO;
import com.rehabit.email.ValidadorEmailService;
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
    private final ValidadorEmailService validadorEmail;
    private final VerificacaoEmailService verificacaoEmail;

    public AuthService(ClinicaRepository clinicaRepository,
                        FisioterapeutaRepository fisioterapeutaRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        ValidadorEmailService validadorEmail,
                        VerificacaoEmailService verificacaoEmail) {
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.validadorEmail = validadorEmail;
        this.verificacaoEmail = verificacaoEmail;
    }

    /**
     * Autentica o usuário verificando o e-mail nas duas tabelas
     * (clínica e fisioterapeuta), já que a tela de login é única
     * para os dois tipos de conta.
     */
    public AuthResponseDTO login(LoginRequestDTO dados) {
        // Normaliza igual ao cadastro: quem digita "Nathan@Gmail.com" no
        // celular (que capitaliza sozinho) precisa entrar do mesmo jeito.
        String email = validadorEmail.normalizar(dados.getEmail());

        Optional<Clinica> clinica = clinicaRepository.findByEmail(email);
        if (clinica.isPresent()) {
            return autenticarClinica(clinica.get(), dados.getSenha());
        }

        Optional<Fisioterapeuta> fisioterapeuta = fisioterapeutaRepository.findByEmail(email);
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
        return new AuthResponseDTO(clinica.getId(), "CLINICA", clinica.getNome(), clinica.getEmail(),
                clinica.getFoto(), clinica.isTutorialVisto(), token);
    }

    private AuthResponseDTO autenticarFisioterapeuta(Fisioterapeuta fisioterapeuta, String senhaInformada) {
        if (!passwordEncoder.matches(senhaInformada, fisioterapeuta.getSenha())) {
            throw new AuthException("E-mail ou senha inválidos.", HttpStatus.UNAUTHORIZED);
        }
        String token = jwtService.gerarToken(fisioterapeuta.getId(), "FISIOTERAPEUTA");
        return new AuthResponseDTO(fisioterapeuta.getId(), "FISIOTERAPEUTA",
                fisioterapeuta.getNome(), fisioterapeuta.getEmail(), fisioterapeuta.getFoto(),
                fisioterapeuta.isTutorialVisto(), token);
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

        // Formato, domínio descartável e DNS do domínio.
        String email = validadorEmail.validarENormalizar(dados.getEmail());

        if (clinicaRepository.existsByEmail(email)
                || fisioterapeutaRepository.existsByEmail(email)) {
            throw new AuthException("Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
        }

        if (clinicaRepository.existsByCnpj(dados.getCnpj().trim())) {
            throw new AuthException("Este CNPJ já está cadastrado.", HttpStatus.CONFLICT);
        }

        // Prova de que a caixa de entrada existe: sem o código que enviamos
        // para lá, o cadastro não passa daqui.
        verificacaoEmail.exigirConfirmado(email);

        Clinica clinica = new Clinica();
        clinica.setNome(dados.getNome());
        clinica.setEmail(email);
        clinica.setSenha(passwordEncoder.encode(dados.getSenha()));
        clinica.setCnpj(dados.getCnpj().trim());
        clinica.setTelefone(vazioParaNulo(dados.getTelefone()));
        clinica.setEndereco(vazioParaNulo(dados.getEndereco()));
        clinica.setSubtitulo(vazioParaNulo(dados.getSubtitulo()));
        clinica.setDescricao(vazioParaNulo(dados.getDescricao()));
        clinica.setFoto(vazioParaNulo(dados.getFoto()));

        Clinica salva = clinicaRepository.save(clinica);
        verificacaoEmail.consumir(email);
        return new AuthResponseDTO(salva.getId(), "CLINICA", salva.getNome(), salva.getEmail(),
                salva.getFoto(), salva.isTutorialVisto());
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
