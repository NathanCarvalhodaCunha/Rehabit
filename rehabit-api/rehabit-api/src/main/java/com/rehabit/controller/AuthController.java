package com.rehabit.controller;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.ConfirmarEmailRequestDTO;
import com.rehabit.dto.EsqueciSenhaRequestDTO;
import com.rehabit.dto.LoginRequestDTO;
import com.rehabit.dto.MensagemDTO;
import com.rehabit.dto.RecuperacaoStatusDTO;
import com.rehabit.dto.RedefinirSenhaRequestDTO;
import com.rehabit.dto.RegisterRequestDTO;
import com.rehabit.dto.VerificacaoEnviadaDTO;
import com.rehabit.dto.VerificarEmailRequestDTO;
import com.rehabit.service.AuthService;
import com.rehabit.service.RecuperacaoSenhaService;
import com.rehabit.service.VerificacaoEmailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // ajuste para o(s) domínio(s) real(is) do front-end em produção
public class AuthController {

    private final AuthService authService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final VerificacaoEmailService verificacaoEmailService;

    public AuthController(AuthService authService,
                          RecuperacaoSenhaService recuperacaoSenhaService,
                          VerificacaoEmailService verificacaoEmailService) {
        this.authService = authService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.verificacaoEmailService = verificacaoEmailService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO dados) {
        return ResponseEntity.ok(authService.login(dados));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> registrar(@Valid @RequestBody RegisterRequestDTO dados) {
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
            @Valid @RequestBody VerificarEmailRequestDTO dados) {
        return ResponseEntity.ok(verificacaoEmailService.enviarCodigo(dados.getEmail()));
    }

    /** Confere o código digitado e libera o e-mail para o /register. */
    @PostMapping("/verificar-email/confirmar")
    public ResponseEntity<MensagemDTO> confirmarCodigoEmail(
            @Valid @RequestBody ConfirmarEmailRequestDTO dados) {
        verificacaoEmailService.confirmarCodigo(dados.getEmail(), dados.getCodigo());
        return ResponseEntity.ok(new MensagemDTO("E-mail confirmado."));
    }

    // ---- Recuperação de senha por e-mail ----

    /**
     * Dispara o e-mail de recuperação. Responde 200 com a mesma mensagem
     * exista ou não a conta, para a tela não virar um jeito de descobrir
     * quais e-mails estão cadastrados.
     */
    @PostMapping("/esqueci-senha")
    public ResponseEntity<MensagemDTO> esqueciSenha(@Valid @RequestBody EsqueciSenhaRequestDTO dados) {
        recuperacaoSenhaService.solicitar(dados);
        return ResponseEntity.ok(new MensagemDTO(recuperacaoSenhaService.getRespostaNeutra()));
    }

    /** Confere o token do link antes de a tela mostrar o formulário. */
    @GetMapping("/recuperar-senha/validar")
    public ResponseEntity<RecuperacaoStatusDTO> validarLinkRecuperacao(@RequestParam String token) {
        return ResponseEntity.ok(recuperacaoSenhaService.validarToken(token));
    }

    /** Grava a nova senha a partir do token do link ou do código de 6 dígitos. */
    @PostMapping("/recuperar-senha")
    public ResponseEntity<MensagemDTO> recuperarSenha(@Valid @RequestBody RedefinirSenhaRequestDTO dados) {
        recuperacaoSenhaService.redefinir(dados);
        return ResponseEntity.ok(new MensagemDTO("Senha redefinida com sucesso."));
    }
}
