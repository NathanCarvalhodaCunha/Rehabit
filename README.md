# Rehabit

Sistema de acompanhamento de fisioterapia para clínicas: cadastro de pacientes e profissionais, registro de sessões e sincronização de medições de amplitude de movimento com um goniômetro digital.

Projeto de conclusão de curso (TCC) — Ensino Médio Técnico.

## Stack

- **Frontend** — HTML, CSS e JavaScript puros (sem framework/bundler), com animações via [GSAP](https://gsap.com/). Duas áreas: `Login/` (autenticação) e `Software/` (aplicação, pós-login).
- **Backend** — [Spring Boot](https://spring.io/projects/spring-boot) 3 (Java 17), banco H2 embarcado, upload de arquivos local.

Cada tela tem uma variante de tema claro e uma escura (ex.: `login.html` / `login-escuro.html`), como páginas HTML estáticas separadas.

## Estrutura

```
index.html        Página inicial: apresentação do projeto (inicio.css, inicio.js, imagens em assets/inicio/)
Login/            Telas de autenticação (login, cadastro, esqueci a senha, redefinir senha)
Software/         Aplicação principal (dashboard, pacientes, sessões, dispositivo, configurações)
Firmware/         Código do goniômetro (ESP32 + MPU6050), teste de hardware e guias de montagem
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

### Conta de demonstração

Com `REHABIT_DEMO=true`, a API cria na subida uma clínica completa para
apresentar e testar o sistema: a **Clínica Movimento Fisioterapia**, com cinco
fisioterapeutas de especialidades diferentes e dezesseis pacientes com
anamnese, metas, meses de sessões com a evolução da amplitude e da dor, faltas,
uma consulta remarcada e a agenda das próximas duas semanas. As datas são
relativas ao dia em que a conta é criada.

| Conta | E-mail |
|---|---|
| Clínica | `contato@movimento.example` |
| Ana Paula Ribeiro (ortopedia) | `ana.ribeiro@movimento.example` |
| Rafael Moura (esportiva) | `rafael.moura@movimento.example` |
| Juliana Castro (neurofuncional) | `juliana.castro@movimento.example` |
| Carlos Eduardo Tanaka (reumatologia) | `carlos.tanaka@movimento.example` |
| Fernanda Oliveira Lima (gerontologia) | `fernanda.lima@movimento.example` |

Todas entram com a senha de `REHABIT_DEMO_SENHA` (padrão: `Rehabit@2026`), e
essa senha é pública: o botão "Entrar na demonstração" da página inicial abre
`Login/login.html?demo` com a conta da Ana Paula já preenchida
([`Login/demonstracao.js`](Login/demonstracao.js)). Por isso a conta é **só de
leitura**: a API recusa com 403 qualquer alteração vinda da clínica de
demonstração ou de um profissional dela (`JwtAuthenticationFilter`), e as telas
mostram um aviso no topo. Se trocar `REHABIT_DEMO_SENHA` no Render, troque
também no `demonstracao.js`. A criação é idempotente — se a clínica já existe,
nada acontece —, então a variável pode ficar ligada. Os e-mails usam o domínio
reservado `.example`, e os telefones são fictícios: para testar o lembrete no
WhatsApp de verdade, use uma conta comum.

**O goniômetro da demonstração é simulado.** Como a senha é pública, a conta
não pareia aparelho nenhum: com um goniômetro de verdade ali, qualquer
visitante mandaria Zerar ou Reiniciar para ele. Para a tela Dispositivo e o
registro de sessão não ficarem parados, o `Software/goniometro.js` simula o
aparelho no navegador (um movimento de abdução do ombro, no formato e no ritmo
do aparelho real), e os botões Zerar, Identificar, captura e Reiniciar agem
sobre a simulação. As telas avisam que é uma simulação. Para mostrar o aparelho
de verdade, use a conta da apresentação (abaixo) ou uma conta comum.

```bash
# local
java -DREHABIT_DEMO=true -jar target/rehabit-api-1.0.0.jar
```

No Render: *Environment → Add Environment Variable* `REHABIT_DEMO` = `true`
(e, se quiser outra senha, `REHABIT_DEMO_SENHA`), depois *Manual Deploy*.

### Conta da apresentação

Para a banca do TCC (27/10/2026) há uma segunda clínica, que funciona como
uma conta comum: a **Clínica Alvorada Fisioterapia**, de Atibaia, com quatro
fisioterapeutas e catorze pacientes. Os pacientes têm anamnese, metas, meses de
sessões, faltas, uma consulta remarcada, altas e um inativo, e a agenda cobre
as próximas três semanas, com boa parte deles marcada às terças, de manhã e à
tarde. Ela é montada pelo mesmo gerador da demonstração
([`GeradorDeClinica`](rehabit-api/rehabit-api/src/main/java/com/rehabit/demo/GeradorDeClinica.java)),
mas tem três diferenças:

- **aceita alterações**: dá para editar, cadastrar, excluir e registrar sessão
  com o goniômetro, e nenhuma tela fala em demonstração. O goniômetro é o de
  verdade: entrando como a clínica ou como um dos profissionais, *Dispositivo →
  Parear novo dispositivo* mostra o código para digitar no portal do aparelho
  (revogar fica só com a clínica);
- **a senha não está no repositório**: ela vem de `REHABIT_APRESENTACAO_SENHA`,
  que não tem valor padrão e precisa passar na política de senha (mínimo de 8
  caracteres e fora da lista das senhas comuns). Sem ela, a API sobe
  normalmente, mas não cria a conta e avisa no log;
- **os e-mails existem**: são apelidos do `tccrehabit@gmail.com`. O Gmail
  entrega na mesma caixa tudo o que tem `+algo` antes do `@`, então o e-mail
  de recuperação de senha de qualquer uma das contas chega à equipe.

| Conta | E-mail |
|---|---|
| Clínica | `tccrehabit+clinica@gmail.com` |
| Mariana Costa Bertolini (ortopedia) | `tccrehabit+mariana@gmail.com` |
| Lucas Henrique Prado (esportiva) | `tccrehabit+lucas@gmail.com` |
| Patrícia Yamamoto Reis (neurofuncional) | `tccrehabit+patricia@gmail.com` |
| Renato Aguiar Fonseca (gerontologia) | `tccrehabit+renato@gmail.com` |

No Render, *Environment*: `REHABIT_APRESENTACAO` = `true` e
`REHABIT_APRESENTACAO_SENHA` = a senha da equipe, depois *Manual Deploy*. O log
mostra `Clínica Alvorada Fisioterapia criada`. Assim como a demonstração, a
criação é idempotente e nunca apaga o que a equipe mudou.

As datas contam a partir do dia em que a conta é criada: o histórico termina na
véspera e a agenda começa no dia seguinte. Se a conta for criada bem antes da
apresentação, os dias que passarem até lá ficam sem sessão, como se ninguém
tivesse sido atendido. Para chegar à banca com tudo em dia, recrie a conta em
26/10: entre como a clínica, vá em *Configurações → Excluir conta* e faça um
*Manual Deploy*. A conta volta com o histórico até 25/10 e a agenda a partir
de 27/10. A exclusão leva junto o goniômetro pareado, então pareie de novo em
*Dispositivo* depois de recriar.

Os pacientes são fictícios, porque dado de saúde de pessoas reais não pode ir
para um repositório público. Os telefones também são inventados. Para mostrar o
lembrete no WhatsApp ao vivo, troque o telefone de um paciente pelo de alguém
da equipe antes de começar.

## Onde o site está publicado

O site fica em **https://rehabit.com.br**, publicado pela
[Vercel](https://vercel.com) (projeto `rehabit`, plano Hobby, entrando com o
GitHub `NathanCarvalhodaCunha`). Cada merge na `main` vai para o ar sozinho, e
cada branch enviado ganha um endereço de prévia `*.vercel.app`. A Vercel serve
a raiz do repositório como site estático (preset *Other*, sem build), e o
[`.vercelignore`](.vercelignore) tira dela o que não é página. A API continua
no Render: o front escolhe a API pelo endereço (`localhost` usa a local,
qualquer outro usa a do Render), então trocar de hospedagem não muda código.

O domínio foi registrado no [registro.br](https://registro.br) e usa o DNS de
lá, no modo avançado, com dois registros:

| Tipo | Nome | Valor |
| --- | --- | --- |
| A | `rehabit.com.br` | `216.198.79.1` |
| CNAME | `www.rehabit.com.br` | `0720e30296230fa6.vercel-dns-017.com` |

O `www` redireciona para o endereço sem `www` (308, configurado em *Domains*
na Vercel). O GitHub Pages (`nathancarvalhodacunha.github.io/Rehabit`) segue
publicando a mesma `main` e serve de reserva.

Ao pôr o site em um endereço novo, dois ajustes fora do repositório:

1. Cadastre o hostname no widget do Turnstile (veja
   [Verificação anti-robô](#verificação-anti-robô-cloudflare-turnstile)).
   Sem isso, todo login naquele endereço recebe 403. Por isso as prévias
   `*.vercel.app` não fazem login: teste o login em `localhost`.
2. No Render, aponte `REHABIT_APP_URL` para a pasta `Login/` do endereço novo
   (`https://rehabit.com.br/Login`), que é o link do e-mail de "esqueci a
   senha". A variável só vale depois de um deploy da API.

## Segurança em produção

O que precisa estar configurado no Render, e o que muda para quem usa:

| Variável | Para quê |
| --- | --- |
| `REHABIT_JWT_SECRET` | **Obrigatória.** Chave que assina as sessões e os tokens dos goniômetros. Gere com `openssl rand -base64 64` (mínimo de 32 bytes). Sem ela a API **não sobe** no Render nem no perfil `cloud`: o repositório não tem mais valor-padrão, porque quem conhece a chave assina token de qualquer conta. Trocá-la encerra todas as sessões e desliga todos os goniômetros, que precisam ser pareados de novo. |
| `REHABIT_PROXIES_CONFIAVEIS` | Quantos proxies ficam na frente da API, para achar o IP real no `X-Forwarded-For` (os limites de tentativas são por IP). Em branco: 1 no Render, 0 fora dele. |
| `REHABIT_LIMITES_ATIVO` | `false` desliga os limites de tentativas (login, cadastro, e-mails, códigos, pareamento, upload). Só para diagnóstico. |

Outros pontos:

- **Limites de tentativas.** Senha errada, código errado e pedido de e-mail
  demais respondem 429 com o tempo de espera. Dez senhas erradas seguidas
  travam aquela conta por 15 minutos (até a senha certa, para o limite não
  revelar a senha) — o "Esqueci minha senha" continua funcionando.
- **Trocar ou redefinir a senha encerra as sessões** abertas em outros
  aparelhos. A tela que fez a troca recebe um token novo e segue logada.
- **Senhas novas** precisam de 8 caracteres e não podem estar entre as mais
  comuns. Contas antigas com senha mais curta continuam entrando.
- **Evolução do banco.** O Hibernate roda com `ddl-auto=update` também no
  PostgreSQL de produção: cria tabelas e colunas novas sozinho, mas nunca
  apaga nem renomeia nada, e um erro de mapeamento pode alterar o esquema
  real. O caminho seguro é passar a migrações versionadas (Flyway) e trocar
  para `validate` — fica como próximo passo.
- **H2 local** não abre mais servidor TCP (`AUTO_SERVER`): para olhar o banco
  com outra ferramenta, pare a API antes.
- **`Rehabit.sql`** não traz mais a linha de exemplo com hash de senha e
  e-mail pessoal. Ela continua no histórico do git: se aquela senha for usada
  em algum lugar, troque-a.
- **Fotos.** O envio (`/api/uploads`) exige login; a foto do cadastro vai
  por `/api/auth/cadastro/foto`, que exige o e-mail já confirmado. Só entra
  JPG, PNG, GIF ou WEBP, reconhecido pelo conteúdo, e os metadados (GPS,
  câmera) saem antes de gravar. O campo `foto` dos cadastros só aceita URL que
  a própria API devolveu, e foto trocada é apagada quando ninguém mais a usa.
  As fotos continuam num endereço público, mas impossível de adivinhar (UUID
  ou id do Cloudinary).
- **Endereço da API no site.** A CSP do `vercel.json` só deixa o site falar
  com `https://rehabit-api-4tex.onrender.com` (e mostrar imagens dele e do
  Cloudinary). Mudou o endereço da API? Troque também em `connect-src` e
  `img-src`, senão o site para de carregar dados. O mesmo vale para o
  firmware, que só confia nas autoridades certificadoras listadas nele (veja
  o guia do goniômetro).
- **Bibliotecas do site** (Toastify, GSAP, Chart.js, jsPDF) ficam em
  `vendor/npm/<pacote>@<versão>/`, servidas pelo próprio site: a CSP não
  confia em script de nenhum CDN. Para atualizar uma, copie o arquivo do
  pacote publicado no npm para a pasta da versão nova, troque o caminho e o
  `integrity` nas páginas — `tests/seguranca-front` confere que o arquivo é
  byte a byte o do npm.
- **Fotos do Cloudinary na CSP.** O `img-src` aceita qualquer conta do
  `res.cloudinary.com`. Confirmado o nome da conta (está no `CLOUDINARY_URL`
  do Render, `cloudinary://...@<nome>`), troque por
  `https://res.cloudinary.com/<nome>/`: assim uma injeção de HTML não usa o
  Cloudinary de outra pessoa para mandar dados para fora.
- **Script inline.** O único é o do tema, no `index.html`, liberado na CSP
  pelo hash do conteúdo. Mexeu nele, recalcule o hash — `tests/seguranca-front`
  acusa a diferença.
- **HSTS** vai sem `includeSubDomains`: não dá para garantir daqui que todo
  subdomínio do domínio do site tem HTTPS. Confirmado isso, pode acrescentar.
- **Testes de segurança.** `mvn test` na API e os de `tests/` no site (veja
  `tests/README.md`). O relatório da auditoria de outubro de 2026 — o que foi
  encontrado, como foi corrigido e o que ainda depende de configuração — está
  em `docs/seguranca/auditoria-2026-10.md`.
- **Varredura de segredos.** Em 14/10/2026, o histórico completo (239
  commits, todos os branches) passou pelo gitleaks 8.21 e por uma busca
  própria: chave dos tokens, Brevo, Cloudinary, Turnstile, banco, chaves
  privadas e senhas de app. Nenhuma senha ou chave de serviço externo
  apareceu. No histórico ficaram só a chave antiga dos tokens, que é o motivo
  de a `REHABIT_JWT_SECRET` ser obrigatória e nova, e o hash da linha antiga
  do `Rehabit.sql`. Para repetir: `gitleaks git --log-opts="--all"` num
  clone completo (`git clone --mirror`).
- **GitHub Pages.** A cópia de reserva em `github.io` não recebe os
  cabeçalhos de segurança do `vercel.json` (CSP, HSTS). O endereço oficial é
  o da Vercel.

## Funcionalidades

- Cadastro e login de clínicas e fisioterapeutas, com confirmação do e-mail por código de 6 dígitos no cadastro.
- Recuperação de senha por e-mail: código de 6 dígitos (e link direto, quando o site tem endereço público).
- Verificação anti-robô (Cloudflare Turnstile) no login e nas telas que mandam e-mail.
- Cadastro de pacientes e vínculo com profissionais.
- Registro de sessões de fisioterapia e histórico de evolução por paciente.
- Goniômetro digital integrado, em tempo real (veja abaixo).
- Tema claro/escuro em todas as telas (a página inicial abre no claro; o escuro fica para quem o escolhe no botão).

## O goniômetro em tempo real

O aparelho é um ESP32 com um MPU6050 preso ao segmento móvel da articulação.
Ele estima a gravidade com um filtro complementar (acelerômetro + giroscópio)
e mede o ângulo entre a gravidade de agora e a gravidade na pose marcada como
zero — com o braço pendurado ao lado do tronco marca 0°, na horizontal marca
90°, acima da cabeça ~180°. Como o zero vem dessa pose e não de um eixo
escolhido no código, não importa em que orientação a placa é amarrada no
braço. Em compensação a medida é sempre positiva (não distingue para que lado
a articulação abriu) e é relativa à gravidade, não ao tronco: o paciente
precisa estar ereto. O ângulo vai para a API; o navegador recebe cada leitura por **SSE**
(`GET /api/goniometro/stream`), sem ficar perguntando de tempos em tempos.
Quando o SSE não sobe — proxy que corta streaming, rede corporativa — o
cliente cai sozinho para polling e continua funcionando.

O aparelho não guarda credencial nenhuma: ele se pareia com a clínica por um
código de 6 dígitos e passa a usar um token só dele, que a clínica pode
revogar a qualquer momento. A telemetria por isso não carrega a clínica no
corpo — ela sai do token, e é o que impede um goniômetro de escrever na
clínica de outro.

O caminho de volta usa a resposta do próprio POST de telemetria: o ESP32 não
abre porta nenhuma, só lê o que veio junto. É assim que os botões da tela
Dispositivo chegam ao aparelho (zerar/tara, identificar, iniciar e parar
captura, reiniciar) e é assim que o servidor dita o ritmo de amostragem —
10 Hz gravando, 2,5 Hz com alguém olhando, 0,5 Hz ocioso, para poupar bateria.

Na prática, dentro do sistema:

- **Tela Dispositivo** — ângulo ao vivo em um mostrador, gráfico dos últimos
  60 segundos, mínimo/máximo/amplitude, bateria, sinal Wi-Fi, número de série,
  firmware, IP e há quanto tempo chegou o último pacote, além da lista de
  aparelhos pareados e do código de pareamento.
- **Cadastrar sessão** — o mesmo canal aparece embutido no formulário: dá para
  usar o ângulo atual ou gravar o movimento completo e deixar a amplitude
  (máximo − mínimo) cair sozinha no campo, que é salvo como medição da sessão.
- **Histórico do paciente** — a sessão que veio de uma gravação guarda também a
  **curva do movimento**, e o botão "Ver curva" na linha do histórico mostra o
  traçado inteiro: dá para ver se o paciente chegou ao máximo de uma vez ou aos
  poucos, se travou no meio, se compensou voltando.

As leituras soltas vivem em memória enquanto a tela está aberta — são centenas
por minuto e só interessam naquele momento. O que vai para o banco é o cadastro
do aparelho, a amplitude que o profissional escolheu gravar na sessão e, quando
ela veio de uma gravação, a curva daquele movimento (uma lista de pares
`[ms desde o início, ângulo]`, decimada acima de 1200 pontos para uma captura
longa continuar cobrindo o movimento inteiro em vez de ser cortada no meio).

A curva é buscada no servidor pela identidade da captura, nunca enviada pelo
navegador: se o profissional corrigir a amplitude à mão, o vínculo se desfaz e
a sessão é salva sem curva — melhor não ter gráfico do que pendurar no paciente
o traçado de um movimento que não corresponde ao número registrado.

Para montar e gravar o aparelho, veja
[`Firmware/goniometro-esp32-GUIA.md`](Firmware/goniometro-esp32-GUIA.md). Para
testar a placa logo depois de soldar, antes do firmware principal, veja
[`Firmware/teste-hardware-GUIA.md`](Firmware/teste-hardware-GUIA.md).

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
| `Brevo respondeu 401: unrecognised IP address` | A chave está certa; a conta do Brevo tem [restrição de IP](https://app.brevo.com/security/authorised_ips) ligada. Hospedado no Render o IP de saída vem de uma [faixa compartilhada](https://render.com/docs/outbound-ip-addresses) e muda, então autorizar o IP que aparece na mensagem resolve só até a próxima troca — o caminho estável é desligar a restrição. |
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

## Verificação anti-robô (Cloudflare Turnstile)

Três rotas da API recebem um token anti-robô da Cloudflare: o **login**
(onde um robô tentaria adivinhar senha), o **envio do código de cadastro** e
o **e-mail de recuperação de senha** — essas duas gastam da cota de 300
e-mails por dia do Brevo, e um robô martelando qualquer uma delas esgotaria a
cota e deixaria todo mundo sem e-mail até o dia seguinte. O `/register` não
precisa: com o envio de e-mail ligado ele exige o código, que só sai por uma
delas.

No login, no cadastro e no "esqueci a senha", a caixinha "Confirme que é
humano" da Cloudflare fica no formulário, acima do botão, e o botão só acende
quando ela fica verde ("Sucesso!"). Quem decide se precisa clicar é a
Cloudflare (o widget está no modo **Managed**): quase sempre a caixinha se
marca sozinha em cerca de um segundo, e só pede o clique quando desconfia —
não existe modo que obrigue o clique toda vez. Quem faz isso é
[`Login/turnstile.js`](Login/turnstile.js); cada página só põe um
`<div class="rh-captcha" data-rh-captcha>` antes do botão.

Cada token vale uma vez só. Depois de usado, a caixinha se confere de novo
sozinha e o botão espera outra vez. Os dois "Enviar de novo" (no pop-up do
código de cadastro e no "esqueci a senha") usam o token que ela já renovou;
se ela estiver escondida ou coberta pelo pop-up e ainda sem token, entra um
widget invisível que só aparece, num cartão no pé da tela, se a Cloudflare
pedir o clique. O token vai no cabeçalho `X-Turnstile-Token`, e a API
confere com a Cloudflare antes de fazer qualquer coisa.

Se a verificação não consegue rodar (script da Cloudflare bloqueado, chave ou
domínio recusados), o botão é liberado e a chamada vai sem token: quem
responde é a API, com a mensagem dela, em vez de um botão morto sem
explicação. Já o veredito "parece robô" (erro `600xxx`) mantém o botão
apagado.

| Onde o site está aberto | O que acontece |
| --- | --- |
| `rehabit.com.br` e GitHub Pages | Caixinha com a site key de `Login/turnstile.js` (vazia = sem verificação) |
| `localhost` | Caixinha com a [chave de teste](https://developers.cloudflare.com/turnstile/troubleshooting/testing/) da Cloudflare, que aprova sempre (com a faixa "Somente para teste") |
| `file://` | Sem verificação — o Turnstile não roda sem endereço; a caixinha some e o botão fica liberado |

Do lado da API, sem `TURNSTILE_SECRET_KEY` a verificação fica desligada,
como o envio de e-mail sem provedor: rodando local e nos testes nada muda, e
o log de arranque diz qual é o caso. Com a chave definida:

- token ausente, inválido, vencido ou já usado → **403** com "Não conseguimos
  confirmar que você não é um robô";
- Cloudflare fora do ar, lenta (mais de 5 s) ou com erro interno → **passa**,
  com aviso no log. Barrar aí trancaria todo mundo do lado de fora por um
  problema que não é de quem está entrando — no dia de uma apresentação, por
  exemplo.

### Como ligar

Precisa de uma conta gratuita na Cloudflare; não precisa de domínio próprio
nem de cartão. **A ordem importa**: com a chave secreta no Render e o site
publicado sem a site key, ninguém consegue entrar.

1. Em [dash.cloudflare.com](https://dash.cloudflare.com) → *Turnstile* →
   *Add widget*: hostnames `rehabit.com.br` e
   `nathancarvalhodacunha.github.io`, modo **Managed**.
2. Copie a **site key** para `CHAVE_DO_SITE` em `Login/turnstile.js` e leve
   para a `main`. Ela é pública: só funciona nos hostnames cadastrados no
   widget. O GitHub Pages publica sozinho em segundos.
3. Abra o site publicado e faça um login: no console do navegador não pode
   aparecer nenhum aviso `[Turnstile]`.
4. Confira que o Render já está com a versão nova da API — no *Logs* dele
   aparece `Verificação anti-robô (Cloudflare Turnstile) desligada`. Merge na
   `main` não garante que ele publicou (já levou dias); se a linha não
   aparecer, rode *Manual Deploy → Deploy latest commit*.
5. Só então, em *Environment* no Render, defina `TURNSTILE_SECRET_KEY` com a
   **secret key** do widget. Ela nunca vai para o repositório. Depois do
   redeploy o log passa a dizer `ligada`.

Para desligar de volta, basta apagar a variável no Render.

### Quando algo dá errado

| Sintoma | O que é |
| --- | --- |
| Todo mundo recebe "Não conseguimos confirmar que você não é um robô" | O site publicado está sem a site key, com a site key de outro widget, ou o hostname não está cadastrado no widget. Olhe os avisos `[Turnstile]` no console. Para destravar na hora, apague a `TURNSTILE_SECRET_KEY` no Render. |
| A caixinha mostra "Falha" e o botão não acende | A Cloudflare reprovou o navegador (console: `[Turnstile] erro 600010`). Recarregar a página costuma resolver; em navegador controlado por automação é o esperado. |
| Console: `[Turnstile] erro 110200` | Hostname não autorizado: cadastre o endereço do site no widget. |
| Console: `[Turnstile] erro 110100` ou `110110` | Site key inválida — confira o que foi copiado para `Login/turnstile.js`. |
| Log da API: `invalid-input-secret` | A `TURNSTILE_SECRET_KEY` está errada. Enquanto isso todo pedido passa — a verificação está ligada só no nome. |
| Log da API: `Não consegui conferir o token anti-robô com a Cloudflare` | A Cloudflare não respondeu a tempo; o pedido passou. Se for constante, é rede do Render. |

## A hibernação da API no Render

O plano gratuito do Render derruba o container depois de **~15 minutos sem
tráfego**. A primeira chamada depois disso paga o religamento inteiro. Medido
no mesmo `POST /api/auth/login`:

| Estado da instância | TTFB |
| --- | --- |
| Dormindo (spin-down) | **146,7 s** |
| Acordada | **0,48 s** |

DNS e TLS levaram 20 ms e 79 ms nos dois casos — a rede nunca foi o problema,
e os assets externos somam só 212 KB, que não explicam minutos.

Por muito tempo isso pareceu um defeito "do primeiro acesso em cada aparelho",
e a confusão tinha uma razão de ser: a tela de login não chama a API ao
carregar, então o HTML aparece rápido e a espera toda cai no botão *Entrar*.
Como a sessão fica no `localStorage` com token de 30 dias, um aparelho já
usado entra direto e **nunca** refaz o login — quem faz a chamada que acorda o
servidor é sempre o aparelho novo. O gatilho real é o tempo parado; o aparelho
novo só é quem paga a conta.

### O que mantém a API acordada

A primeira tentativa foi um workflow do GitHub batendo em `/api/health` a cada
10 min, e ela **não funcionou**. O agendador do GitHub Actions é "melhor
esforço": na virada de cada hora, sob carga, ele atrasa e descarta execuções.
Pedindo 108 por dia, entregou **5 a 6**, com intervalos de 2 a 7 horas, e todas
encontraram a API dormindo (124 a 146 s de resposta). Ninguém percebeu por
dias porque cada execução saía verde: o 200 acabava chegando, e o aviso de
"instância dormindo" ficava escondido embaixo do check.

Hoje o relógio é o da própria API:

- **`ManterApiAcordada`** (`rehabit-api/.../service/`) chama o próprio
  `/api/health` a cada **5 min**, pela URL **pública**. Tem de ser a pública, e
  não `localhost`: o Render só conta como tráfego o que passa pelo proxy de
  entrada dele. A URL vem de `RENDER_EXTERNAL_URL`, que o Render define sozinho,
  então não há nada para configurar. Fora do Render essa variável não existe e o
  ping fica desligado, para que rodar a API local não saia batendo na produção.
- **O `/api/health` mostra se está funcionando**, sem precisar dos logs do
  Render:

  ```json
  {"status":"ok","uptimeSegundos":5400,"autoPing":true,"ultimoAutoPingSegundosAtras":42}
  ```

  `autoPing: false` em produção quer dizer que ele está desligado.
  `ultimoAutoPingSegundosAtras` acima de ~300, com a instância de pé há mais de
  10 min, quer dizer que ele parou de funcionar.
- **O workflow** [`manter-api-acordada.yml`](.github/workflows/manter-api-acordada.yml)
  virou rede de segurança e alarme. Ele acorda a instância se ela parar por
  outro motivo (o auto-ping não roda com o processo parado) e **falha**, com
  e-mail do GitHub, se encontrar a API dormindo ou o auto-ping parado. Antes
  de uma apresentação, dá para rodá-lo na mão: aba *Actions* → *Manter a API
  acordada* → *Run workflow*.
- **O loader** avisa quando a espera passa do normal (5 s e 25 s), para o caso
  em que alguém ainda pegar a instância subindo, logo depois de um deploy.

**Horas do plano gratuito.** Acordada 24 h, a instância gasta ~720 a 744 horas
por mês das **750** gratuitas. Isso só fecha se ela for o **único** web service
gratuito da conta no Render (o front-end está no GitHub Pages, então não
conta). Se houver outro, as horas acabam antes do fim do mês e o Render suspende
os serviços: nesse caso, defina `REHABIT_KEEPALIVE_ATIVO=false` no Render e
desative o workflow. A solução definitiva, se um dia houver orçamento, é o plano
pago, que não hiberna.

## Licença

Distribuído sob a licença MIT, em nome da Equipe Rehabit — veja [LICENSE](LICENSE).
