package com.rehabit.service;

import com.rehabit.dto.EsqueciSenhaRequestDTO;
import com.rehabit.dto.RecuperacaoStatusDTO;
import com.rehabit.dto.RedefinirSenhaRequestDTO;
import com.rehabit.email.EmailService;
import com.rehabit.email.TemplatesEmail;
import com.rehabit.email.ValidadorEmailService;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.RecuperacaoSenha;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.RecuperacaoSenhaRepository;
import com.rehabit.security.CodigoSeguro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * "Esqueci minha senha" por e-mail.
 *
 * Fluxo: a pessoa informa o e-mail, recebe um código de 6 dígitos (e um link,
 * quando o site tem endereço público configurado) e usa um dos dois para
 * gravar a senha nova. Token e código valem uma vez só e expiram.
 *
 * Substitui a redefinição antiga por CNPJ/COFFITO: CNPJ é dado público —
 * qualquer um que soubesse o e-mail da clínica trocava a senha dela.
 */
@Service
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);

    private static final int MAX_TENTATIVAS = 5;

    /**
     * Mesma resposta para e-mail cadastrado e não cadastrado: senão a tela
     * de recuperação vira um jeito de descobrir quem tem conta no sistema.
     */
    private static final String RESPOSTA_NEUTRA =
            "Se existir uma conta com esse e-mail, enviamos as instruções de recuperação para ele.";

    private final RecuperacaoSenhaRepository recuperacaoRepository;
    private final ClinicaRepository clinicaRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final ValidadorEmailService validadorEmail;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final int expiracaoMinutos;
    private final String urlLogin;

    public RecuperacaoSenhaService(RecuperacaoSenhaRepository recuperacaoRepository,
                                   ClinicaRepository clinicaRepository,
                                   FisioterapeutaRepository fisioterapeutaRepository,
                                   ValidadorEmailService validadorEmail,
                                   EmailService emailService,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${rehabit.recuperacao.expiracao-minutos:30}") int expiracaoMinutos,
                                   @Value("${rehabit.app.url-login:}") String urlLogin) {
        this.recuperacaoRepository = recuperacaoRepository;
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.validadorEmail = validadorEmail;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.expiracaoMinutos = expiracaoMinutos;
        this.urlLogin = urlLogin == null ? "" : urlLogin.trim().replaceAll("/+$", "");
    }

    public String getRespostaNeutra() {
        return RESPOSTA_NEUTRA;
    }

    /** Recebe o pedido e, se a conta existir, dispara o e-mail de recuperação. */
    @Transactional
    public void solicitar(EsqueciSenhaRequestDTO dados) {
        String email = validadorEmail.normalizar(dados.getEmail());
        // Aproveita o pedido para varrer os vencidos: a tabela é pequena e o
        // projeto não tem agendador — mesma abordagem do pareamento.
        recuperacaoRepository.deleteByExpiraEmBefore(LocalDateTime.now().minusDays(1));

        String nome;
        String tipoConta;

        Optional<Clinica> clinica = clinicaRepository.findByEmail(email);
        if (clinica.isPresent()) {
            nome = clinica.get().getNome();
            tipoConta = "CLINICA";
        } else {
            Optional<Fisioterapeuta> fisioterapeuta = fisioterapeutaRepository.findByEmail(email);
            if (fisioterapeuta.isEmpty()) {
                // Sem conta: não manda nada, mas o controller responde igual.
                log.info("Pedido de recuperação para e-mail sem conta: {}", EmailService.mascarar(email));
                return;
            }
            nome = fisioterapeuta.get().getNome();
            tipoConta = "FISIOTERAPEUTA";
        }

        // Um pedido novo invalida os anteriores: senão um link antigo,
        // esquecido numa caixa de entrada, continuaria abrindo a conta.
        recuperacaoRepository.deleteByEmail(email);
        recuperacaoRepository.flush();

        String token = CodigoSeguro.gerarToken();
        String codigo = CodigoSeguro.gerarCodigoNumerico(6);
        LocalDateTime agora = LocalDateTime.now();

        RecuperacaoSenha pedido = new RecuperacaoSenha();
        pedido.setEmail(email);
        pedido.setTipoConta(tipoConta);
        pedido.setTokenHash(CodigoSeguro.hash(token));
        pedido.setCodigoHash(CodigoSeguro.hash(codigo));
        pedido.setTentativas(0);
        pedido.setCriadoEm(agora);
        pedido.setExpiraEm(agora.plusMinutes(expiracaoMinutos));
        recuperacaoRepository.save(pedido);

        String link = montarLink(token);
        emailService.enviar(email,
                "Redefinir sua senha — Rehabit",
                TemplatesEmail.recuperacaoSenhaHtml(primeiroNome(nome), codigo, link, expiracaoMinutos),
                TemplatesEmail.recuperacaoSenhaTexto(primeiroNome(nome), codigo, link, expiracaoMinutos));
    }

    /** Confere o token do link antes de a tela mostrar o formulário de nova senha. */
    @Transactional(readOnly = true)
    public RecuperacaoStatusDTO validarToken(String token) {
        if (token == null || token.isBlank()) {
            return new RecuperacaoStatusDTO(false, null, "Link inválido.");
        }

        Optional<RecuperacaoSenha> pedido = recuperacaoRepository.findByTokenHash(CodigoSeguro.hash(token.trim()));
        if (pedido.isEmpty() || pedido.get().getUsadoEm() != null) {
            return new RecuperacaoStatusDTO(false, null,
                    "Este link já foi usado ou não é mais válido. Peça um novo.");
        }
        if (pedido.get().getExpiraEm().isBefore(LocalDateTime.now())) {
            return new RecuperacaoStatusDTO(false, null, "Este link expirou. Peça um novo.");
        }
        return new RecuperacaoStatusDTO(true, EmailService.mascarar(pedido.get().getEmail()), null);
    }

    /** Grava a nova senha, validando o token do link ou o código digitado. */
    @Transactional
    public void redefinir(RedefinirSenhaRequestDTO dados) {
        RecuperacaoSenha pedido = temToken(dados)
                ? localizarPorToken(dados.getToken().trim())
                : localizarPorCodigo(dados);

        String email = pedido.getEmail();
        String senhaCodificada = passwordEncoder.encode(dados.getNovaSenha());

        if ("CLINICA".equals(pedido.getTipoConta())) {
            Clinica clinica = clinicaRepository.findByEmail(email)
                    .orElseThrow(() -> new AuthException("Conta não encontrada.", HttpStatus.BAD_REQUEST));
            clinica.setSenha(senhaCodificada);
            clinicaRepository.save(clinica);
        } else {
            Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findByEmail(email)
                    .orElseThrow(() -> new AuthException("Conta não encontrada.", HttpStatus.BAD_REQUEST));
            fisioterapeuta.setSenha(senhaCodificada);
            fisioterapeutaRepository.save(fisioterapeuta);
        }

        pedido.setUsadoEm(LocalDateTime.now());
        recuperacaoRepository.save(pedido);
        log.info("Senha redefinida por e-mail para {}", EmailService.mascarar(email));
    }

    private boolean temToken(RedefinirSenhaRequestDTO dados) {
        return dados.getToken() != null && !dados.getToken().isBlank();
    }

    private RecuperacaoSenha localizarPorToken(String token) {
        RecuperacaoSenha pedido = recuperacaoRepository.findByTokenHash(CodigoSeguro.hash(token))
                .orElseThrow(() -> new AuthException(
                        "Este link já foi usado ou não é mais válido. Peça um novo.", HttpStatus.BAD_REQUEST));
        garantirUtilizavel(pedido);
        return pedido;
    }

    private RecuperacaoSenha localizarPorCodigo(RedefinirSenhaRequestDTO dados) {
        if (dados.getEmail() == null || dados.getEmail().isBlank()
                || dados.getCodigo() == null || dados.getCodigo().isBlank()) {
            throw new AuthException("Informe o e-mail e o código que você recebeu.", HttpStatus.BAD_REQUEST);
        }

        String email = validadorEmail.normalizar(dados.getEmail());
        List<RecuperacaoSenha> pedidos = recuperacaoRepository
                .findByEmailAndUsadoEmIsNullAndExpiraEmAfterOrderByCriadoEmDesc(email, LocalDateTime.now());

        AuthException invalido = new AuthException(
                "Código inválido ou expirado. Peça um novo código.", HttpStatus.BAD_REQUEST);
        if (pedidos.isEmpty()) {
            throw invalido;
        }

        RecuperacaoSenha pedido = pedidos.get(0);
        if (pedido.getTentativas() >= MAX_TENTATIVAS) {
            recuperacaoRepository.delete(pedido);
            throw new AuthException("Muitas tentativas erradas. Peça um novo código.", HttpStatus.BAD_REQUEST);
        }

        if (!CodigoSeguro.confere(dados.getCodigo().trim(), pedido.getCodigoHash())) {
            pedido.setTentativas(pedido.getTentativas() + 1);
            recuperacaoRepository.save(pedido);
            int restantes = MAX_TENTATIVAS - pedido.getTentativas();
            throw new AuthException(
                    restantes > 0
                            ? "Código incorreto. Você ainda tem " + restantes
                                    + (restantes == 1 ? " tentativa." : " tentativas.")
                            : "Código incorreto. Peça um novo código.",
                    HttpStatus.BAD_REQUEST);
        }
        return pedido;
    }

    private void garantirUtilizavel(RecuperacaoSenha pedido) {
        if (pedido.getUsadoEm() != null) {
            throw new AuthException("Este link já foi usado. Peça um novo.", HttpStatus.BAD_REQUEST);
        }
        if (pedido.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new AuthException("Este link expirou. Peça um novo.", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Link direto para a tela de nova senha. Só existe quando REHABIT_APP_URL
     * aponta para a pasta Login/ publicada; rodando por file:// não há URL
     * para montar e o e-mail vai só com o código.
     */
    private String montarLink(String token) {
        if (urlLogin.isEmpty()) {
            return null;
        }
        return urlLogin + "/redefinir-senha.html?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /** "Clínica Movimento Ltda" -> "Clínica": e-mail com nome inteiro soa a mala direta. */
    private String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "tudo bem"; // completa o "Olá, ..." do template sem ficar truncado
        }
        return nome.trim().split("\\s+")[0];
    }
}
