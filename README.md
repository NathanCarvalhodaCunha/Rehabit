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
recuperação de senha. Sem SMTP configurado a API **continua funcionando** —
ela escreve o conteúdo do e-mail (com o código) no console do backend, o que
basta para desenvolver e demonstrar, e o cadastro passa a exigir só as
checagens de endereço (formato, domínio descartável e DNS).

Para enviar de verdade, o jeito mais simples é copiar
`rehabit-api/rehabit-api/config/application.properties.exemplo` para
`application.properties` na mesma pasta e preencher os dados. O Spring Boot lê
essa pasta sozinho ao subir o jar, sem recompilar e sem mexer em variável de
ambiente — e o arquivo está no `.gitignore`, então a senha não vai parar no
repositório. Com Gmail, use uma
[senha de app](https://support.google.com/accounts/answer/185833): a senha
normal da conta não funciona.

```properties
# rehabit-api/rehabit-api/config/application.properties
spring.mail.username=suaconta@gmail.com
spring.mail.password=abcd efgh ijkl mnop
```

Em servidor (Render e afins), onde não dá para deixar arquivo na máquina, as
mesmas opções existem como variáveis de ambiente:

| Variável | Para que serve | Padrão |
| --- | --- | --- |
| `MAIL_USERNAME` | Conta que envia. Vazio = envio desligado. | *(vazio)* |
| `MAIL_PASSWORD` | Senha de app da conta acima. | *(vazio)* |
| `MAIL_HOST` / `MAIL_PORT` | Servidor SMTP. | `smtp.gmail.com` / `587` |
| `MAIL_FROM` | Endereço no remetente. | o `MAIL_USERNAME` |
| `REHABIT_APP_URL` | Endereço público da pasta `Login/`, usado para montar o link do e-mail de recuperação. Vazio = o e-mail vai só com o código. | *(vazio)* |
| `REHABIT_VALIDAR_EMAIL_DNS` | Checar no DNS se o domínio do e-mail recebe mensagens. | `true` |
| `REHABIT_CONFIRMAR_CADASTRO` | Exigir o código de confirmação no cadastro. Vazio = liga sozinho quando há SMTP. | *(vazio)* |

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
