# Rehabit

Sistema de acompanhamento de fisioterapia para clínicas: cadastro de pacientes e profissionais, registro de sessões e sincronização de medições de amplitude de movimento com um goniômetro digital.

Projeto de conclusão de curso (TCC) — Ensino Médio Técnico.

## Stack

- **Frontend** — HTML, CSS e JavaScript puros (sem framework/bundler), com animações via [GSAP](https://gsap.com/). Duas áreas: `Login/` (autenticação) e `Software/` (aplicação, pós-login).
- **Backend** — [Spring Boot](https://spring.io/projects/spring-boot) 3 (Java 17), banco H2 embarcado, upload de arquivos local.

Cada tela tem uma variante de tema claro e uma escura (ex.: `login.html` / `login-escuro.html`), como páginas HTML estáticas separadas.

## Estrutura

```
Login/            Telas de autenticação (login, cadastro, esqueci a senha, redefinir senha)
Software/         Aplicação principal (dashboard, pacientes, sessões, dispositivo, configurações)
rehabit-api/      Backend Spring Boot (API REST + banco H2 embarcado)
Rehabit.sql       Script de referência do schema do banco
iniciar-rehabit.bat   Compila e sobe o backend, depois abre o site
```

## Como rodar

Pré-requisito: JDK 17+ instalado.

```bash
iniciar-rehabit.bat
```

O script compila o backend (Maven Wrapper, não precisa Maven instalado), sobe a API em `http://localhost:8080` e abre a tela de login no navegador padrão.

Para rodar manualmente:

```bash
cd rehabit-api/rehabit-api
mvnw.cmd -q package -DskipTests
java -jar target/rehabit-api-1.0.0.jar
```

E abra `Login/login.html` diretamente no navegador (o frontend é servido como arquivo local, sem servidor de desenvolvimento).

## Funcionalidades

- Cadastro e login de clínicas e fisioterapeutas, com confirmação do e-mail por código de 6 dígitos no cadastro.
- Recuperação de senha por e-mail: código de 6 dígitos (e link direto, quando o site tem endereço público).
- Cadastro de pacientes e vínculo com profissionais.
- Registro de sessões de fisioterapia e histórico de evolução por paciente.
- Sincronização de medições de um goniômetro digital (amplitude de movimento articular).
- Tema claro/escuro em todas as telas.

## Envio de e-mail

Duas telas dependem de e-mail: a confirmação do endereço no cadastro e a
recuperação de senha. Sem provedor configurado a API **continua funcionando** —
ela escreve o conteúdo do e-mail (com o código) no console do backend, o que
basta para desenvolver e demonstrar, e o cadastro passa a exigir só as
checagens de endereço (formato, domínio descartável e DNS).

Há dois caminhos de saída, escolhidos por qual estiver configurado.

### Em produção (Render): API HTTP do Brevo

Desde 26/09/2025 o plano gratuito do Render
[bloqueia a saída nas portas de SMTP](https://render.com/changelog/free-web-services-will-no-longer-allow-outbound-traffic-to-smtp-ports)
(25, 465 e 587). Ou seja: lá o Gmail por SMTP não funciona, com senha de app
certa ou errada — a conexão morre antes de chegar ao servidor. Por isso o
envio de produção usa a [API HTTP do Brevo](https://developers.brevo.com/reference/sendtransacemail),
que responde em HTTPS na porta 443.

O plano gratuito do Brevo dá 300 e-mails por dia e aceita verificar **só um
endereço remetente** — não exige domínio próprio, então serve uma conta
Gmail comum.

1. Crie a conta em [brevo.com](https://www.brevo.com) e verifique o e-mail
   que vai aparecer como remetente (*Senders, Domains & Dedicated IPs →
   Senders*). Chega uma mensagem de confirmação nesse endereço.
2. Gere uma chave em *SMTP & API → API Keys*. Ela começa com `xkeysib-`.
3. No Render, em *Environment*, defina:

| Variável | Valor |
| --- | --- |
| `BREVO_API_KEY` | a chave `xkeysib-...` |
| `MAIL_FROM` | o endereço verificado no passo 1 |
| `REHABIT_APP_URL` | endereço público da pasta `Login/`, para o link do e-mail de recuperação |

Se `BREVO_API_KEY` estiver definida e `MAIL_FROM` não, a API avisa no log e
volta a escrever no console — o Brevo recusaria o envio de um remetente não
verificado.

### Para rodar local: SMTP

Fora do Render as portas de SMTP funcionam normalmente. O jeito mais simples
é copiar `rehabit-api/rehabit-api/config/application.properties.exemplo` para
`application.properties` na mesma pasta e preencher. O Spring Boot lê essa
pasta sozinho ao subir o jar, sem recompilar e sem mexer em variável de
ambiente — e o arquivo está no `.gitignore`, então a senha não vai parar no
repositório. Com Gmail, use uma
[senha de app](https://support.google.com/accounts/answer/185833): a senha
normal da conta não funciona.

```properties
# rehabit-api/rehabit-api/config/application.properties
spring.mail.username=suaconta@gmail.com
spring.mail.password=abcd efgh ijkl mnop
```

### Todas as opções

| Variável | Para que serve | Padrão |
| --- | --- | --- |
| `BREVO_API_KEY` | Chave da API do Brevo. Definida, é o caminho usado. | *(vazio)* |
| `MAIL_FROM` | Endereço remetente. Obrigatório com o Brevo, e precisa estar verificado lá. | o `MAIL_USERNAME` |
| `MAIL_FROM_NAME` | Nome exibido como remetente. | `Rehabit` |
| `MAIL_USERNAME` | Conta do SMTP. Usada quando não há chave do Brevo. | *(vazio)* |
| `MAIL_PASSWORD` | Senha de app da conta acima. | *(vazio)* |
| `MAIL_HOST` / `MAIL_PORT` | Servidor SMTP. | `smtp.gmail.com` / `587` |
| `REHABIT_APP_URL` | Endereço público da pasta `Login/`, usado para montar o link do e-mail de recuperação. Vazio = o e-mail vai só com o código. | *(vazio)* |
| `REHABIT_VALIDAR_EMAIL_DNS` | Checar no DNS se o domínio do e-mail recebe mensagens. | `true` |
| `REHABIT_CONFIRMAR_CADASTRO` | Exigir o código de confirmação no cadastro. Vazio = liga sozinho quando há provedor. | *(vazio)* |

A linha que o backend escreve ao subir diz qual caminho está valendo:
`Envio de e-mail por BREVO...`, `Envio de e-mail por SMTP...` ou
`Envio de e-mail desligado...`.

### Quando o envio falha

O erro vai inteiro para o log do backend, com uma dica do que conferir. Os
que aparecem na configuração inicial:

| No log | O que é |
| --- | --- |
| `Brevo respondeu 401: Key not found` | A chave não é reconhecida. Ou é a **chave de SMTP** (`xsmtpsib-`), que só vale para o relay SMTP, ou é a versão **mascarada** — o Brevo mostra a chave inteira uma única vez, e o que aparece na tela depois não funciona. Nos dois casos, gere uma chave de API v3 nova. |
| `Brevo respondeu 400: sender is not valid` | O endereço em `MAIL_FROM` não está verificado na conta do Brevo. |
| `Brevo respondeu 402` ou `429` | Passou dos 300 e-mails do dia. |
| `MailConnectException: Couldn't connect to host` (por SMTP) | O bloqueio de portas do Render. Use o Brevo. |
| `AuthenticationFailedException: 535` (por SMTP) | Senha de app do Gmail errada. |

O formato da chave também é conferido no arranque: se ela tiver cara de
chave de SMTP ou de chave mascarada, o log avisa antes de alguém tentar se
cadastrar.

### Como o e-mail é validado no cadastro

1. **Formato** — regra mais rígida que a padrão, que aceitaria coisas como `a@b`.
2. **Domínio descartável** — bloqueia serviços de e-mail temporário
   (lista em `src/main/resources/emails-descartaveis.txt`).
3. **DNS** — consulta o registro MX do domínio; sem servidor de e-mail
   publicado, aquele endereço não pode existir.
4. **Código por e-mail** — a prova final: sem abrir a caixa de entrada e
   digitar os 6 dígitos, a conta não é criada.

## Licença

Distribuído sob a licença MIT — veja [LICENSE](LICENSE).
