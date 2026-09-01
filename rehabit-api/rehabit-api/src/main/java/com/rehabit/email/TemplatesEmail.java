package com.rehabit.email;

/**
 * Monta o HTML dos e-mails transacionais. São montados aqui, em Java, e não
 * com um motor de template (Thymeleaf e afins) porque são só dois e-mails —
 * uma dependência a mais não se pagaria.
 */
public final class TemplatesEmail {

    private static final String MARCA = "#1565D8";

    private TemplatesEmail() {
    }

    /** E-mail de recuperação de senha: código de 6 dígitos e, se houver, link direto. */
    public static String recuperacaoSenhaHtml(String nome, String codigo, String link, int minutos) {
        String botao = link == null || link.isBlank() ? "" : """
                <tr>
                  <td align="center" style="padding:8px 0 4px;">
                    <a href="%s" style="display:inline-block;background:%s;color:#ffffff;text-decoration:none;
                       font-weight:600;font-size:16px;padding:13px 30px;border-radius:8px;">Redefinir minha senha</a>
                  </td>
                </tr>
                <tr>
                  <td align="center" style="padding:10px 0 0;font-size:13px;color:#8A93A0;">
                    ou use o código abaixo na tela de redefinição
                  </td>
                </tr>
                """.formatted(escapar(link), MARCA);

        return layout("Redefinir sua senha", """
                <p style="margin:0 0 14px;font-size:16px;color:#2B3440;">Olá, %s!</p>
                <p style="margin:0 0 22px;font-size:16px;color:#2B3440;line-height:1.55;">
                  Recebemos um pedido para redefinir a senha da sua conta Rehabit.
                  %s
                </p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                  %s
                  <tr>
                    <td align="center" style="padding:14px 0 4px;">
                      <div style="display:inline-block;background:#F1F5FB;border:1px solid #DDE5F1;border-radius:10px;
                                  padding:14px 26px;font-size:30px;font-weight:700;letter-spacing:8px;color:%s;">%s</div>
                    </td>
                  </tr>
                </table>
                <p style="margin:24px 0 0;font-size:14px;color:#5F6C7B;line-height:1.55;">
                  O código vale por <strong>%d minutos</strong> e só pode ser usado uma vez.
                  Se não foi você quem pediu, ignore este e-mail — sua senha continua a mesma.
                </p>
                """.formatted(
                escapar(nome),
                link == null || link.isBlank() ? "Use o código abaixo para criar uma nova senha." : "",
                botao, MARCA, codigo, minutos));
    }

    public static String recuperacaoSenhaTexto(String nome, String codigo, String link, int minutos) {
        StringBuilder sb = new StringBuilder();
        sb.append("Olá, ").append(nome).append("!\n\n")
          .append("Recebemos um pedido para redefinir a senha da sua conta Rehabit.\n\n")
          .append("Código de recuperação: ").append(codigo).append("\n");
        if (link != null && !link.isBlank()) {
            sb.append("Link direto: ").append(link).append("\n");
        }
        sb.append("\nO código vale por ").append(minutos).append(" minutos e só pode ser usado uma vez.\n")
          .append("Se não foi você quem pediu, ignore este e-mail — sua senha continua a mesma.\n");
        return sb.toString();
    }

    /** E-mail de confirmação de cadastro: prova que a caixa de entrada existe mesmo. */
    public static String verificacaoCadastroHtml(String codigo, int minutos) {
        return layout("Confirme seu e-mail", """
                <p style="margin:0 0 14px;font-size:16px;color:#2B3440;">Bem-vindo(a) ao Rehabit!</p>
                <p style="margin:0 0 22px;font-size:16px;color:#2B3440;line-height:1.55;">
                  Para concluir seu cadastro, digite o código abaixo na tela de cadastro.
                  Ele confirma que este e-mail é seu de verdade.
                </p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                  <tr>
                    <td align="center" style="padding:6px 0 4px;">
                      <div style="display:inline-block;background:#F1F5FB;border:1px solid #DDE5F1;border-radius:10px;
                                  padding:14px 26px;font-size:30px;font-weight:700;letter-spacing:8px;color:%s;">%s</div>
                    </td>
                  </tr>
                </table>
                <p style="margin:24px 0 0;font-size:14px;color:#5F6C7B;line-height:1.55;">
                  O código vale por <strong>%d minutos</strong>.
                  Se não foi você quem tentou criar uma conta, é só ignorar este e-mail.
                </p>
                """.formatted(MARCA, codigo, minutos));
    }

    public static String verificacaoCadastroTexto(String codigo, int minutos) {
        return "Bem-vindo(a) ao Rehabit!\n\n"
                + "Código de confirmação do cadastro: " + codigo + "\n\n"
                + "O código vale por " + minutos + " minutos.\n"
                + "Se não foi você quem tentou criar uma conta, é só ignorar este e-mail.\n";
    }

    private static String layout(String titulo, String conteudo) {
        return """
                <!doctype html>
                <html lang="pt-BR">
                  <body style="margin:0;padding:0;background:#F4F6FA;
                               font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F4F6FA;padding:32px 16px;">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                                 style="max-width:520px;background:#ffffff;border-radius:14px;overflow:hidden;
                                        box-shadow:0 8px 30px -18px rgba(15,30,60,.35);">
                            <tr>
                              <td style="background:%s;padding:22px 28px;">
                                <span style="color:#ffffff;font-size:22px;font-weight:700;letter-spacing:-.01em;">Rehabit</span>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:28px;">
                                <h1 style="margin:0 0 18px;font-size:21px;font-weight:700;color:#0E1726;">%s</h1>
                                %s
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:16px 28px 24px;border-top:1px solid #EDF0F5;font-size:12px;color:#9AA0AC;">
                                Este é um e-mail automático do Rehabit. Não responda a esta mensagem.
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(MARCA, escapar(titulo), conteudo);
    }

    private static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
