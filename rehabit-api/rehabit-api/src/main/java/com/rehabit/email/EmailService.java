package com.rehabit.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.rehabit.exception.AuthException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Envio dos e-mails transacionais do Rehabit (recuperação de senha e
 * confirmação de cadastro).
 *
 * Há dois caminhos de saída, escolhidos por qual configuração existe:
 *
 * - BREVO_API_KEY definida — envia pela API HTTP do Brevo. É o caminho de
 *   produção, porque o plano gratuito do Render bloqueia as portas de SMTP
 *   (25, 465 e 587) desde setembro de 2025; por HTTPS na 443 passa.
 * - Só MAIL_USERNAME/MAIL_PASSWORD — envia por SMTP. Serve para rodar
 *   local e para hospedagem que não bloqueia essas portas.
 * - Nenhuma das duas — escreve o e-mail no console. Assim dá para
 *   desenvolver e demonstrar o fluxo inteiro sem depender de provedor.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private enum Transporte { BREVO, SMTP, CONSOLE }

    private final JavaMailSender mailSender;
    private final BrevoClient brevo;
    private final Transporte transporte;
    private final String remetente;
    private final String nomeRemetente;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String usuarioSmtp,
                        @Value("${rehabit.mail.remetente:}") String remetente,
                        @Value("${rehabit.mail.nome-remetente:Rehabit}") String nomeRemetente,
                        @Value("${rehabit.mail.brevo.api-key:}") String chaveBrevo,
                        @Value("${rehabit.mail.brevo.url:}") String urlBrevo) {
        this.mailSender = mailSender;
        this.nomeRemetente = nomeRemetente;
        this.remetente = primeiroPreenchido(remetente, usuarioSmtp);

        boolean temBrevo = !vazio(chaveBrevo);
        boolean temSmtp = !vazio(usuarioSmtp);

        if (temBrevo && vazio(this.remetente)) {
            // O Brevo só aceita enviar de um endereço verificado na conta
            // dele, e não temos de onde deduzir qual é. Sem isso, todo
            // envio voltaria com "sender not valid".
            this.transporte = Transporte.CONSOLE;
            log.error("BREVO_API_KEY configurada, mas MAIL_FROM está vazio. Defina MAIL_FROM com o "
                    + "endereço verificado no Brevo. Enquanto isso, os e-mails vão para este console.");
        } else if (temBrevo) {
            this.transporte = Transporte.BREVO;
        } else if (temSmtp) {
            this.transporte = Transporte.SMTP;
        } else {
            this.transporte = Transporte.CONSOLE;
            log.warn("Envio de e-mail desligado (sem BREVO_API_KEY e sem MAIL_USERNAME). "
                    + "Os códigos de recuperação e de cadastro serão escritos neste console.");
        }

        this.brevo = this.transporte == Transporte.BREVO
                ? new BrevoClient(chaveBrevo.trim(), urlBrevo)
                : null;

        if (this.transporte != Transporte.CONSOLE) {
            log.info("Envio de e-mail por {}, remetente {}", this.transporte, mascarar(this.remetente));
        }
    }

    /** Se algum caminho de envio de verdade está configurado. */
    public boolean isHabilitado() {
        return transporte != Transporte.CONSOLE;
    }

    /**
     * Envia um e-mail HTML. Sem provedor configurado, registra o conteúdo
     * no log em vez de falhar: o fluxo continua funcionando em ambiente de
     * desenvolvimento.
     */
    public void enviar(String destinatario, String assunto, String corpoHtml, String corpoTexto) {
        switch (transporte) {
            case BREVO -> enviarPorBrevo(destinatario, assunto, corpoHtml, corpoTexto);
            case SMTP -> enviarPorSmtp(destinatario, assunto, corpoHtml, corpoTexto);
            case CONSOLE -> escreverNoConsole(destinatario, assunto, corpoTexto);
        }
    }

    private void enviarPorBrevo(String destinatario, String assunto, String corpoHtml, String corpoTexto) {
        try {
            brevo.enviar(remetente, nomeRemetente, destinatario, assunto, corpoHtml, corpoTexto);
            log.info("E-mail \"{}\" enviado para {} pelo Brevo", assunto, mascarar(destinatario));
        } catch (IOException ex) {
            // Exceção inteira, não só getMessage(): uma falha de conexão vem
            // com mensagem nula e o log ficaria dizendo apenas "null".
            log.error("Falha ao enviar o e-mail para {} pelo Brevo", mascarar(destinatario), ex);
            throw falhaNoEnvio();
        }
    }

    private void enviarPorSmtp(String destinatario, String assunto, String corpoHtml, String corpoTexto) {
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(remetente, nomeRemetente, StandardCharsets.UTF_8.name()));
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(corpoTexto, corpoHtml);
            mailSender.send(mensagem);
            log.info("E-mail \"{}\" enviado para {} por SMTP", assunto, mascarar(destinatario));
        } catch (UnsupportedEncodingException | jakarta.mail.MessagingException ex) {
            log.error("Falha ao montar o e-mail para {}", mascarar(destinatario), ex);
            throw falhaNoEnvio();
        } catch (org.springframework.mail.MailException ex) {
            log.error("Falha ao enviar o e-mail para {} por SMTP", mascarar(destinatario), ex);
            throw falhaNoEnvio();
        }
    }

    private void escreverNoConsole(String destinatario, String assunto, String corpoTexto) {
        log.warn("""

                ===== E-MAIL NÃO ENVIADO (nenhum provedor configurado) =====
                Para:    {}
                Assunto: {}
                {}
                ============================================================
                """, destinatario, assunto, corpoTexto);
    }

    private AuthException falhaNoEnvio() {
        return new AuthException("Não foi possível enviar o e-mail. Tente novamente em alguns instantes.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String primeiroPreenchido(String preferido, String alternativo) {
        if (!vazio(preferido)) {
            return preferido.trim();
        }
        return vazio(alternativo) ? "" : alternativo.trim();
    }

    /** "nathan@gmail.com" -> "nat***@gmail.com" (para não jogar e-mail inteiro no log). */
    public static String mascarar(String email) {
        if (email == null) {
            return "";
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        String local = email.substring(0, arroba);
        String dominio = email.substring(arroba);
        if (local.length() <= 3) {
            return local.charAt(0) + "***" + dominio;
        }
        return local.substring(0, 3) + "***" + dominio;
    }
}
