package com.rehabit.controller;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.ConfirmarEmailRequestDTO;
import com.rehabit.dto.EsqueciSenhaRequestDTO;
import com.rehabit.dto.LoginRequestDTO;
import com.rehabit.dto.MensagemDTO;
import com.rehabit.dto.RecuperacaoStatusDTO;
import com.rehabit.dto.RedefinirSenhaRequestDTO;
import com.rehabit.dto.RegisterRequestDTO;
import com.rehabit.dto.ValidarLinkRecuperacaoDTO;
import com.rehabit.dto.VerificacaoEnviadaDTO;
import com.rehabit.dto.VerificarEmailRequestDTO;
import com.rehabit.security.TurnstileService;
import com.rehabit.service.AuthService;
import com.rehabit.service.RecuperacaoSenhaService;
import com.rehabit.service.VerificacaoEmailService;
import com.rehabit.storage.EnvioDeFoto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // ajuste para o(s) domínio(s) real(is) do front-end em produção
public class AuthController {

    private final AuthService authService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final VerificacaoEmailService verificacaoEmailService;
    private final TurnstileService turnstileService;
    private final EnvioDeFoto envioDeFoto;

    public AuthController(AuthService authService,
                          RecuperacaoSenhaService recuperacaoSenhaService,
                          VerificacaoEmailService verificacaoEmailService,
                          TurnstileService turnstileService,
                          EnvioDeFoto envioDeFoto) {
        this.authService = authService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.verificacaoEmailService = verificacaoEmailService;
        this.turnstileService = turnstileService;
        this.envioDeFoto = envioDeFoto;
    }

    // As rotas que recebem o token anti-robô (TurnstileService) são as que um
    // robô teria interesse em martelar: o login, para adivinhar senha, e as
    // duas que mandam e-mail. O /register depende: com o código por e-mail
    // ligado, quem chega a ele já passou pela verificação ao pedir o código;
    // sem o código, é a única porta da criação de contas e confere o token.
    // Além disso, todas têm limite de tentativas por IP (LimitePorIpInterceptor).

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO dados,
            @RequestHeader(value = TurnstileService.CABECALHO, required = false) String tokenAntiRobo) {
        turnstileService.verificar(tokenAntiRobo);
        return ResponseEntity.ok(authService.login(dados));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> registrar(
            @Valid @RequestBody RegisterRequestDTO dados,
            @RequestHeader(value = TurnstileService.CABECALHO, required = false) String tokenAntiRobo) {
        if (!verificacaoEmailService.isObrigatorio()) {
            turnstileService.verificar(tokenAntiRobo);
        }
        AuthResponseDTO resposta = authService.registrar(dados);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    // ---- Confirmação do e-mail no cadastro ----

    /** Diz à tela de cadastro se ela precisa pedir o código de confirmação. */
    @GetMapping("/verificar-email/obrigatorio")
    public ResponseEntity<Boolean> confirmacaoObrigatoria() {
        return ResponseEntity.ok(verificacaoEmailService.isObrigatorio());
    }

    /** Valida o endereço e manda o código de 6 dígitos para ele. */
    @PostMapping("/verificar-email/enviar")
    public ResponseEntity<VerificacaoEnviadaDTO> enviarCodigoEmail(
            @Valid @RequestBody VerificarEmailRequestDTO dados,
            @RequestHeader(value = TurnstileService.CABECALHO, required = false) String tokenAntiRobo) {
        turnstileService.verificar(tokenAntiRobo);
        return ResponseEntity.ok(verificacaoEmailService.enviarCodigo(dados.getEmail()));
    }

    /** Confere o código digitado e libera o e-mail para o /register. */
    @PostMapping("/verificar-email/confirmar")
    public ResponseEntity<MensagemDTO> confirmarCodigoEmail(
            @Valid @RequestBody ConfirmarEmailRequestDTO dados) {
        verificacaoEmailService.confirmarCodigo(dados.getEmail(), dados.getCodigo());
        return ResponseEntity.ok(new MensagemDTO("E-mail confirmado."));
    }

    /**
     * A foto do cadastro sobe antes de a conta existir, então não há token
     * para pedir. Em troca, só passa com o e-mail já confirmado pelo código —
     * o mesmo que o /register vai exigir em seguida —, com limite por e-mail
     * e por IP. Era o que /api/uploads aberto a qualquer um fazia, sem nada
     * disso.
     */
    @PostMapping("/cadastro/foto")
    public ResponseEntity<Map<String, String>> enviarFotoDoCadastro(@RequestParam("arquivo") MultipartFile arquivo,
                                                                    @RequestParam("email") String email) {
        verificacaoEmailService.exigirConfirmadoParaFoto(email);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", envioDeFoto.salvar(arquivo)));
    }

    // ---- Recuperação de senha por e-mail ----

    /**
     * Dispara o e-mail de recuperação. Responde 200 com a mesma mensagem
     * exista ou não a conta, para a tela não virar um jeito de descobrir
     * quais e-mails estão cadastrados.
     */
    @PostMapping("/esqueci-senha")
    public ResponseEntity<MensagemDTO> esqueciSenha(
            @Valid @RequestBody EsqueciSenhaRequestDTO dados,
            @RequestHeader(value = TurnstileService.CABECALHO, required = false) String tokenAntiRobo) {
        turnstileService.verificar(tokenAntiRobo);
        recuperacaoSenhaService.solicitar(dados);
        return ResponseEntity.ok(new MensagemDTO(recuperacaoSenhaService.getRespostaNeutra()));
    }

    /**
     * Confere o token do link antes de a tela mostrar o formulário. O token
     * vai no corpo: na query string ele ficava nos logs de acesso da
     * hospedagem, que guardam a URL de cada requisição.
     */
    @PostMapping("/recuperar-senha/validar")
    public ResponseEntity<RecuperacaoStatusDTO> validarLinkRecuperacao(
            @Valid @RequestBody ValidarLinkRecuperacaoDTO dados) {
        return ResponseEntity.ok(recuperacaoSenhaService.validarToken(dados.getToken()));
    }

    /**
     * A versão antiga, com o token na URL, para a tela publicada antes desta
     * mudança (o site e a API são publicados em separado). Pode sair quando o
     * site novo estiver no ar.
     */
    @GetMapping("/recuperar-senha/validar")
    public ResponseEntity<RecuperacaoStatusDTO> validarLinkRecuperacaoAntigo(@RequestParam String token) {
        return ResponseEntity.ok(recuperacaoSenhaService.validarToken(token));
    }

    /** Grava a nova senha a partir do token do link ou do código de 6 dígitos. */
    @PostMapping("/recuperar-senha")
    public ResponseEntity<MensagemDTO> recuperarSenha(@Valid @RequestBody RedefinirSenhaRequestDTO dados) {
        recuperacaoSenhaService.redefinir(dados);
        return ResponseEntity.ok(new MensagemDTO("Senha redefinida com sucesso."));
    }
}
