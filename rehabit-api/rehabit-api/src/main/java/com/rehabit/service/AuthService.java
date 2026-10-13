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
import com.rehabit.security.CodigoSeguro;
import com.rehabit.security.JwtService;
import com.rehabit.security.Limite;
import com.rehabit.security.LimiteDeTentativas;
import com.rehabit.storage.FotosEmUso;
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
    private final LimiteDeTentativas limites;
    private final FotosEmUso fotos;

    /**
     * Hash de uma senha que ninguém sabe, cifrado pelo mesmo encoder (e
     * portanto com o mesmo custo) das senhas de verdade. Veja login().
     */
    private final String hashSemConta;

    public AuthService(ClinicaRepository clinicaRepository,
                        FisioterapeutaRepository fisioterapeutaRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        ValidadorEmailService validadorEmail,
                        VerificacaoEmailService verificacaoEmail,
                        LimiteDeTentativas limites,
                        FotosEmUso fotos) {
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.validadorEmail = validadorEmail;
        this.verificacaoEmail = verificacaoEmail;
        this.limites = limites;
        this.fotos = fotos;
        this.hashSemConta = passwordEncoder.encode(CodigoSeguro.gerarToken());
    }

    /**
     * Autentica o usuário verificando o e-mail nas duas tabelas
     * (clínica e fisioterapeuta), já que a tela de login é única
     * para os dois tipos de conta.
     *
     * Conta inexistente e senha errada saem iguais — mesma mensagem, mesmo
     * status e o mesmo tempo de resposta. Sem conta, a senha é conferida
     * contra um hash qualquer só para gastar o BCrypt: antes, a resposta sem
     * conta voltava umas 15 vezes mais rápido, e o relógio dizia quem tinha
     * conta. Pelo mesmo motivo o limite por conta conta as duas falhas.
     */
    public AuthResponseDTO login(LoginRequestDTO dados) {
        // Normaliza igual ao cadastro: quem digita "Nathan@Gmail.com" no
        // celular (que capitaliza sozinho) precisa entrar do mesmo jeito.
        String email = validadorEmail.normalizar(dados.getEmail());
        limites.exigirFolga(Limite.LOGIN_POR_CONTA, email);

        Optional<Clinica> clinica = clinicaRepository.findByEmail(email);
        if (clinica.isPresent()) {
            if (passwordEncoder.matches(dados.getSenha(), clinica.get().getSenha())) {
                return autenticarClinica(clinica.get());
            }
            throw recusar(email);
        }

        Optional<Fisioterapeuta> fisioterapeuta = fisioterapeutaRepository.findByEmail(email);
        if (fisioterapeuta.isPresent()) {
            if (passwordEncoder.matches(dados.getSenha(), fisioterapeuta.get().getSenha())) {
                return autenticarFisioterapeuta(fisioterapeuta.get());
            }
            throw recusar(email);
        }

        passwordEncoder.matches(dados.getSenha(), hashSemConta);
        throw recusar(email);
    }

    private AuthException recusar(String email) {
        limites.contar(Limite.LOGIN_POR_CONTA, email);
        return new AuthException("E-mail ou senha inválidos.", HttpStatus.UNAUTHORIZED);
    }

    private AuthResponseDTO autenticarClinica(Clinica clinica) {
        String token = jwtService.gerarToken(clinica.getId(), "CLINICA");
        AuthResponseDTO resposta = new AuthResponseDTO(clinica.getId(), "CLINICA", clinica.getNome(),
                clinica.getEmail(), clinica.getFoto(), clinica.isTutorialVisto(), token);
        resposta.setNomeClinica(clinica.getNome());
        return resposta;
    }

    private AuthResponseDTO autenticarFisioterapeuta(Fisioterapeuta fisioterapeuta) {
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

        // Formato, domínio descartável e DNS do domínio.
        String email = validadorEmail.validarENormalizar(dados.getEmail());

        // Prova de que a caixa de entrada existe: sem o código que enviamos
        // para lá, o cadastro não passa daqui. Vem antes das checagens de
        // "já cadastrado" para que só o dono do e-mail chegue a elas — senão
        // o 409 de um e-mail com conta e o 403 dos outros diziam a qualquer
        // um, sem gastar e-mail nenhum, quem é cliente.
        verificacaoEmail.exigirConfirmado(email);

        if (clinicaRepository.existsByEmail(email)
                || fisioterapeutaRepository.existsByEmail(email)) {
            throw new AuthException("Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
        }

        if (clinicaRepository.existsByCnpj(dados.getCnpj().trim())) {
            throw new AuthException("Este CNPJ já está cadastrado.", HttpStatus.CONFLICT);
        }

        Clinica clinica = new Clinica();
        clinica.setNome(dados.getNome());
        clinica.setEmail(email);
        clinica.setSenha(passwordEncoder.encode(dados.getSenha()));
        clinica.setCnpj(dados.getCnpj().trim());
        clinica.setTelefone(vazioParaNulo(dados.getTelefone()));
        clinica.setEndereco(vazioParaNulo(dados.getEndereco()));
        clinica.setSubtitulo(vazioParaNulo(dados.getSubtitulo()));
        clinica.setDescricao(vazioParaNulo(dados.getDescricao()));
        clinica.setFoto(fotos.aceitar(dados.getFoto(), null));

        Clinica salva = clinicaRepository.save(clinica);
        verificacaoEmail.consumir(email);
        return new AuthResponseDTO(salva.getId(), "CLINICA", salva.getNome(), salva.getEmail(),
                salva.getFoto(), salva.isTutorialVisto());
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
