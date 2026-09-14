package com.rehabit.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Map<String, String>> resposta(HttpStatus status, String mensagem) {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("mensagem", mensagem);
        return ResponseEntity.status(status).body(corpo);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> tratarAuthException(AuthException ex) {
        return resposta(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> tratarValidacao(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erro -> erro.getDefaultMessage())
                .orElse("Dados inválidos.");
        return resposta(HttpStatus.BAD_REQUEST, mensagem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> tratarIntegridade(DataIntegrityViolationException ex) {
        return resposta(HttpStatus.BAD_REQUEST,
                "Não foi possível salvar: dados obrigatórios ausentes ou duplicados.");
    }

    // ---- Erros de quem chamou (400/404/405) ----
    //
    // Sem estes, tudo abaixo caía no tratarGenerico() e virava 500: um JSON
    // torto, uma data em formato errado ou uma rota inexistente ficavam
    // indistinguíveis de uma falha do servidor — tanto para a tela, que
    // mostrava "Erro interno no servidor", quanto para quem lê o log.

    /** Corpo ausente, JSON malformado ou campo com tipo/formato errado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> tratarCorpoIlegivel(HttpMessageNotReadableException ex) {
        return resposta(HttpStatus.BAD_REQUEST,
                "Não foi possível ler os dados enviados. Confira os campos e tente novamente.");
    }

    /** Parâmetro de rota ou de query com tipo errado (ex.: /pacientes/abc). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> tratarTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Valor inválido para '" + ex.getName() + "'.");
    }

    /** Falta um parâmetro obrigatório na query (ex.: /busca sem o q). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> tratarParametroAusente(MissingServletRequestParameterException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Informe o parâmetro '" + ex.getParameterName() + "'.");
    }

    /** Rota inexistente (inclui arquivo de /uploads que não está mais lá). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> tratarRotaInexistente(NoResourceFoundException ex) {
        return resposta(HttpStatus.NOT_FOUND, "Recurso não encontrado.");
    }

    /** Método HTTP que a rota não aceita (ex.: DELETE em /pacientes/{id}). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> tratarMetodoNaoSuportado(HttpRequestMethodNotSupportedException ex) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido para este endereço.");
    }

    // ---- Conexão fechada do outro lado ----

    /**
     * O cliente sumiu antes de a resposta terminar: aba do SSE fechada, rede
     * caída. Não é falha do servidor e não há o que responder — o canal já
     * morreu. Quem cuida do lado do goniômetro é o GoniometroStream, que
     * descarta o emitter; aqui só resta não tratar isso como erro.
     *
     * Fica em DEBUG de propósito: a tela Dispositivo abre e fecha um stream a
     * cada visita, e no ERROR isso enterraria os problemas de verdade em
     * stack traces de desconexão normal.
     *
     * As IOException de disco e de e-mail não chegam aqui — o armazenamento e
     * o EmailService já as capturam e traduzem antes.
     */
    @ExceptionHandler(IOException.class)
    public void tratarConexaoFechada(IOException ex) {
        log.debug("Cliente fechou a conexão antes do fim da resposta", ex);
    }

    // ---- Falha de verdade do servidor ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> tratarGenerico(Exception ex) {
        // A resposta continua genérica (não vaza detalhe interno para a tela),
        // mas o que aconteceu precisa ficar no log: antes a exceção era
        // engolida aqui e um bug real não deixava rastro nenhum.
        log.error("Erro não tratado na API", ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor.");
    }
}
