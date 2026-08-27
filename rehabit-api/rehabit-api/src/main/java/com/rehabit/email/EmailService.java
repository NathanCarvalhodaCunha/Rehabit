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

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Envio dos e-mails transacionais do Rehabit (recuperação de senha e
 * confirmação de cadastro).
 *
 * Sem MAIL_USERNAME/MAIL_PASSWORD configurados o envio fica desligado e o
 * conteúdo do e-mail vai para o console — assim dá para desenvolver e
 * demonstrar o fluxo inteiro sem depender de um servidor SMTP.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String remetente;
    private final String nomeRemetente;
    private final boolean habilitado;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String usuarioSmtp,
                        @Value("${rehabit.mail.remetente:}") String remetente,
                        @Value("${rehabit.mail.nome-remetente:Rehabit}") String nomeRemetente) {
        this.mailSender = mailSender;
        this.remetente = (remetente == null || remetente.isBlank()) ? usuarioSmtp : remetente.trim();
        this.nomeRemetente = nomeRemetente;
        this.habilitado = usuarioSmtp != null && !usuarioSmtp.isBlank();

        if (!habilitado) {
            log.warn("Envio de e-mail desligado (MAIL_USERNAME não configurado). "
                    + "Os códigos de recuperação e de cadastro serão escritos neste console.");
        }
    }

    /** Se há SMTP configurado. Quem chama decide o que fazer quando não há. */
    public boolean isHabilitado() {
        return habilitado;
    }

    /**
     * Envia um e-mail HTML. Quando o SMTP não está configurado, registra o
     * conteúdo no log em vez de falhar: o fluxo continua funcionando em
     * ambiente de desenvolvimento.
     */
    public void enviar(String destinatario, String assunto, String corpoHtml, String corpoTexto) {
        if (!habilitado) {
            log.warn("""

                    ===== E-MAIL NÃO ENVIADO (SMTP não configurado) =====
                    Para:    {}
                    Assunto: {}
                    {}
                    =====================================================
                    """, destinatario, assunto, corpoTexto);
            return;
        }

        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(remetente, nomeRemetente, StandardCharsets.UTF_8.name()));
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(corpoTexto, corpoHtml);
            mailSender.send(mensagem);
            log.info("E-mail \"{}\" enviado para {}", assunto, mascarar(destinatario));
        } catch (UnsupportedEncodingException | jakarta.mail.MessagingException ex) {
            log.error("Falha ao montar o e-mail para {}", mascarar(destinatario), ex);
            throw new AuthException("Não foi possível enviar o e-mail. Tente novamente em alguns instantes.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        } catch (org.springframework.mail.MailException ex) {
            log.error("Falha ao enviar o e-mail para {}", mascarar(destinatario), ex);
            throw new AuthException("Não foi possível enviar o e-mail. Tente novamente em alguns instantes.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
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
