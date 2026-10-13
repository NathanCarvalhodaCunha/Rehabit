# Auditoria de segurança do Rehabit — outubro de 2026

Branch: `ccr-81dc4b05-1x3i6j` (base: `7322333`, o `main` depois do PR #29).

## 1. Escopo e método

**Analisado:** a API (Spring Boot), o site (Login/ e Software/, publicados pela Vercel), o firmware do goniômetro (ESP32), a configuração (`application*.properties`, `vercel.json`, `.vercelignore`, workflow do GitHub, Dockerfile, `iniciar-rehabit.bat`), as dependências, o banco (`Rehabit.sql` e o mapeamento JPA) e o histórico do repositório.

**Como:**
1. Leitura do código com o OWASP Top 10 como roteiro: controle de acesso, injeção, XSS, autenticação e sessão, exposição de dados, configuração insegura, componentes desatualizados e tratamento de erros.
2. Reprodução de cada achado **localmente**: API em localhost com H2, site servido localmente com os cabeçalhos do `vercel.json`, navegador sem resolver nenhum host além de localhost. **Nenhuma requisição foi feita à produção** (rehabit.com.br, onrender.com, Vercel, Cloudinary, Brevo, Cloudflare).
3. Antes da correção, cada achado foi confirmado ou descartado por uma segunda verificação independente.
4. Depois das correções, três revisões independentes (API, site e firmware/infra) procuraram desvios das correções e problemas novos. O que encontraram está na seção 4, já corrigido.

**Regra de prova:** só conta como corrigido o que tem teste. Cada teste de regressão foi rodado também **contra o código antigo** ou com a correção desfeita, para mostrar que ele de fato pega o problema.

## 2. Resumo

| | Crítica | Alta | Média | Baixa | Info | Total |
|---|---|---|---|---|---|---|
| Auditoria (SEC-01 a SEC-36) | 3 | 8 | 11 | 11 | 3 | 36 |
| Reauditoria (R-01 a R-18) | — | 3 | 3 | 10 | 2 | 18 |

- **Corrigidos no código, com teste:** todos os achados que dependem só do código.
- **Corrigidos, sem teste possível aqui:** o firmware (só compilação, sem hardware) e o container sem root (sem Docker).
- **Dependem de configuração da produção ou de decisão de projeto:** listados nas seções 6 e 7.

**Combinações que aumentavam o risco antes das correções:**

- **Chave dos tokens publicada (SEC-01) + token de goniômetro aceito em qualquer rota (SEC-05):** qualquer pessoa com o código-fonte assinava um token de clínica ou de profissional e lia os prontuários de qualquer clínica.
- **XSS armazenado (SEC-02/03/24) + token no localStorage sem CSP (SEC-09):** um profissional mal-intencionado gravava um "nome" ou uma "foto" que, aberto pela clínica, roubava a sessão dela — a conta com mais poder no sistema.
- **Pareamento sem limite (SEC-04) + token de goniômetro valendo nas rotas de tela (SEC-05):** força bruta no código de 6 dígitos de qualquer clínica com pareamento aberto, e depois leitura de dados de outra clínica.
- **Upload público (SEC-07) + tipo pelo nome do arquivo (SEC-06):** HTML hospedado e servido pela própria API.
- **HTTPS sem verificação no firmware (SEC-11) + portal Wi-Fi que abria sozinho com senha pública (SEC-22):** alguém por perto reconfigurava o aparelho para uma rede própria e lia o token (válido por um ano).

## 3. Vulnerabilidades da auditoria, correções e testes

| ID | Sev. | Onde | Problema | Correção | Teste |
|---|---|---|---|---|---|
| SEC-01 | Crítica | API: `JwtService`, `application.properties` | Chave dos tokens com valor-padrão escrito no repositório: quem lê o código assina token de qualquer conta. | Sem valor-padrão; em produção a API não sobe sem `REHABIT_JWT_SECRET` (≥ 32 bytes); fora dela, chave sorteada a cada subida. | `ChaveDosTokensNaSubidaTest` |
| SEC-02 | Crítica | API (campo `foto`) + site (5 telas) | XSS armazenado: a foto, texto livre, ia para `style="background-image:url(...)"`; com aspas, virava HTML/script na tela de quem abrisse (profissional → clínica). | API só aceita URL que o próprio armazenamento devolveu; site aplica a foto pelo CSSOM e só aceita http(s)/`/uploads/`. | `CampoFotoTest`, `tests/seguranca-front` (xss) |
| SEC-03 | Crítica | Site: ~20 telas | XSS armazenado: nome, especialidade, observação, notificação etc. montados com `innerHTML` sem escape. | `escaparHtml` único (`& < > " '`) em todo dado que vira HTML. | `tests/seguranca-front` (xss, 20+ telas) |
| SEC-04 | Alta | API: `/api/dispositivos/parear` | Código de pareamento de 6 dígitos sem limite de tentativas: força bruta entregava token de goniômetro de qualquer clínica com código ativo. | Limite de erros por IP (10/10 min) e teto global (30/min). | `LimitesDeTentativasTest` |
| SEC-05 | Alta | API: `JwtAuthenticationFilter` | Token de goniômetro aceito nas rotas de tela: o id do aparelho virava id de profissional de outra clínica (leitura de pacientes, troca de senha). | Token de dispositivo só vale em telemetria/leitura; nas demais, 403. | `TokenDoGoniometroSoNasRotasDoAparelhoTest` |
| SEC-06 | Média | API: upload | Confiava no Content-Type e na extensão do cliente: HTML/SVG gravados e servidos pela API. | Tipo pelos bytes (JPEG/PNG/GIF/WEBP), extensão do tipo real, SVG recusado; `/uploads/**` só serve imagem, com CSP `sandbox` e `nosniff`. | `EnvioDeFotosTest` |
| SEC-07 | Média | API: `/api/uploads` | Upload público e sem limite (abuso de armazenamento/cota). | Exige login; foto do cadastro por rota própria que exige e-mail confirmado; limites por IP e por e-mail. | `EnvioDeFotosTest`, `FotoDoCadastroTest` |
| SEC-08 | Alta | API: telemetria | `"angulo": 1e999999999` fazia o `setScale` montar um número de um bilhão de dígitos: um pacote de 16 bytes travava um núcleo da CPU. | Faixa e dígitos conferidos antes de qualquer conta (DTO + serviço). | `AnguloDoGoniometroTest` |
| SEC-09 | Alta | Site: `vercel.json` | Sem CSP, HSTS nem Permissions-Policy; token no localStorage sem nenhuma contenção. | CSP sem script inline (o do tema por hash), HSTS, Permissions-Policy, frame-ancestors. | `tests/seguranca-front` (estatico, csp) |
| SEC-10 | Alta | API: login, cadastro, e-mail, códigos | Sem limite de tentativas; anti-robô opcional. | Limites por IP, por conta e por destinatário; IP só do proxy de confiança. | `LimitesDeTentativasTest` |
| SEC-11 | Alta | Firmware | `setInsecure()`: HTTPS sem conferir certificado; na mesma rede, dava para se passar pela API e ler o token do aparelho. | Certificado conferido contra 7 raízes embutidas. | Compilação nos núcleos 2.0.9 e 3.3.2; conferência do bloco PEM |
| SEC-12 | Média | API: exclusão de conta | Apagava qualquer arquivo citado nas linhas da conta: bastava pôr a URL da foto de outra clínica e excluir a conta. | Só apaga arquivo que nenhum cadastro usa mais. | `CampoFotoTest` |
| SEC-13 | Média | API: fotos de pacientes | Foto trocada nunca era apagada; metadados (GPS) publicados junto. | Foto trocada apagada; EXIF/GPS removidos no envio. Endereço continua público, mas impossível de adivinhar. | `CampoFotoTest`, `EnvioDeFotosTest` |
| SEC-14 | Média | API: cadastro e login | Enumeração de contas: 409 no código de cadastro, oráculo de tempo no login. | Mesma resposta para quem tem e quem não tem conta; login sem conta gasta o mesmo BCrypt; conflito só depois do e-mail confirmado. | `CodigoDeCadastroNaoRevelaContaTest`, `LimitesDeTentativasTest` |
| SEC-15 | Média | API: sessão | Tokens de 30 dias sobreviviam à troca e à redefinição de senha. | Troca/redefinição derruba os tokens anteriores; a tela que trocou recebe token novo. | `SessaoEncerradaAoTrocarASenhaTest` |
| SEC-16 | Baixa | API + site: SSE | Token de sessão na URL do stream. | Ingresso de uso único, 60 s, preso à clínica. | `IngressoDoStreamTest` |
| SEC-17 | Média | API: DTOs | Textos livres sem limite (TEXT); corpo sem teto. | `@Size` em todos os campos de texto; corpo JSON até 256 KB. | `TamanhoDosDadosTest` |
| SEC-18 | Baixa | API: telemetria | Ângulos fora da faixa física iam para o prontuário. | Faixa −5° a 185° (bruto ±360°). | `AnguloDoGoniometroTest` |
| SEC-19 | Média | API: comandos | Com dois aparelhos na clínica, o outro levava a tara/captura/REINICIAR. | Comando vai para o aparelho que estava mandando leituras; REINICIAR registrado em log. | `ComandoPorAparelhoTest` |
| SEC-20 | Média | API: SSE | Sem teto de conexões. | 3 por pessoa (a mais antiga cai), 20 por clínica, 500 no servidor. | `GoniometroStreamTest`, `ComandoPorAparelhoTest` |
| SEC-21 | Baixa | Firmware | Token em texto claro na memória do ESP32. | Documentado (criptografia da flash queima fusíveis); Revogar anula. | — (residual) |
| SEC-22 | Alta | Firmware | Portal Wi-Fi com senha publicada abria sozinho e sem tempo limite quando a rede caía. | Com rede salva, só abre segurando o BOOT 2 s, por 3 min. | Compilação nos dois núcleos |
| SEC-23 | Média | Site | CDN sem SRI e sem versão fixa. | Versão fixa + `integrity` em todas as tags. | `tests/seguranca-front` (estatico) |
| SEC-24 | Alta | Site | `escaparHtml` não escapava aspas: injeção de atributo em `<option>`. | Escape de `"` e `'`. | `tests/seguranca-front` (xss) |
| SEC-25 | Baixa | API: dependências | Spring Boot 3.3.0 / Tomcat 10.1.24 desatualizados. | Spring Boot 3.5.16, jjwt 0.12.7. | Suíte inteira |
| SEC-26 | Média | API: produção | `ddl-auto=update` no PostgreSQL. | Documentado; migrações (Flyway) como próximo passo. | — (residual) |
| SEC-27 | Baixa | API: tutorial-visto | Qualquer conta marcava o tutorial de outra. | Só a própria conta. | `TutorialVistoTest` |
| SEC-28 | Baixa | API | Senha mínima de 6, sem lista de comuns. | 8 caracteres, máx. 72 bytes, fora das mais comuns. | `PoliticaDeSenhaTest` |
| SEC-29 | Baixa | Site | Mensagem de erro da API sem escape. | Escapada. | `tests/seguranca-front` (xss) |
| SEC-30 | Baixa | Site: agenda .ics | Injeção de iCalendar (CR/LF). | Escape de CR/LF, `;`, `,`, `\` e dobra de linha. | `tests/seguranca-front` (ics) |
| SEC-31 | Baixa | API | `metaAmplitude` com escala absurda → 500. | `@Digits`/faixa → 400. | `AnguloDoGoniometroTest` |
| SEC-32 | Baixa | API | Upload > 5 MB → 500 com stack trace. | 413 com mensagem. | `EnvioDeFotosTest` |
| SEC-33 | Baixa | API local | H2 com `AUTO_SERVER` (TCP aberto, sa sem senha). | Removido. | Revisão de configuração |
| SEC-34 | Info | API: validação de e-mail | Consulta DNS de domínio escolhido pelo usuário. | Limites e anti-robô no cadastro; resto documentado. | — |
| SEC-35 | Info | Vercel | README (detalhes de operação) publicado no site. | `.vercelignore` exclui README e `tests/`. | Revisão de configuração |
| SEC-36 | Info | `Rehabit.sql` | Linha com hash de senha e e-mail pessoal. | Removida (continua no histórico do git). | — |

## 4. Reauditoria (achados novos, todos corrigidos)

| ID | Sev. | Onde | Problema | Correção | Prova |
|---|---|---|---|---|---|
| R-01 | Média | API: `LimitePorIpInterceptor` | Limites por IP contornáveis com caminho codificado: `/api/auth/l%6Fgin` chegava ao login sem contar. Reproduzido: 25 tentativas do mesmo IP com teto de 20. | Compara com a rota que o Spring escolheu; HEAD conta como GET. | `LimitesDeTentativasTest.caminhoCodificadoNaoFogeDoLimite` (falha sem a correção) |
| R-02 | Média | API: código de cadastro | SEC-14 ainda aberto num ponto: o pedido de código respondia 409 "já cadastrado". | Mesma resposta para quem tem e quem não tem conta; o dono da caixa recebe um aviso no lugar do código. | `CodigoDeCadastroNaoRevelaContaTest` (falha sem a correção) |
| R-03 | Baixa | API: remarcar consulta | Observação sem limite de tamanho nessa rota. | Conferida no serviço. | `TamanhoDosDadosTest` |
| R-04 | Alta | API: banco | *Open Session In View* fazia cada stream SSE segurar uma conexão do banco por até 30 min. Com o pool de 10, a 11ª tela do goniômetro derrubava a API inteira. | `spring.jpa.open-in-view=false` (o modelo não tem associações JPA). | `IngressoDoStreamTest.telasAbertasNaoSeguramConexaoDoBanco` (falha com o OSIV ligado) |
| R-05 | Baixa | API: SSE | O 403/429 do stream virava 500 com `Accept: text/event-stream`; o ingresso era emitido para clínica alheia. | Erro do stream sai só com o status; posse conferida já na emissão do ingresso. | `IngressoDoStreamTest` |
| R-06 | Baixa | API: fotos | Metadados que sobreviviam: EXIF em WEBP, blocos privados/C2PA em PNG, comentários em GIF, miniatura no JFIF. | Lista do que fica (e não do que sai) nos quatro formatos. | `EnvioDeFotosTest` (4 formatos); fuzz de 80 mil mutações sem travamento nem 500 |
| R-07 | Baixa | API: corpo | O teto de 256 KB era dispensado para qualquer `multipart`; corpo de formulário acima do teto podia virar 500. | Dispensa só nas duas rotas de foto; `FormContentFilter` desligado. | `TamanhoDosDadosTest` |
| R-08 | Baixa | API: uploads | Sem limite por conta; fotos enviadas e nunca usadas ficavam para sempre. | 100 por conta por dia; limpeza diária do que ninguém usa (`tb13_arquivo_enviado`). | `LimitesDeTentativasTest`, `LimpezaDeEnviosTest` |
| R-09 | Alta | Firmware 2.4 | Introduzido na correção do SEC-22: `getWiFiIsSaved()` com o Wi-Fi desligado respondia com lixo da memória. Um aparelho configurado podia abrir o portal sem tempo limite a cada boot. | `WiFi.mode(WIFI_STA)` antes da checagem. | Revisão da biblioteca; compilação nos núcleos 2.0.9 e 3.3.2 |
| R-10 | Alta | Firmware | O portal servia as páginas do WiFiManager para gravar outro firmware e apagar a configuração, sem senha. Por elas se lia o token e a senha do Wi-Fi da clínica. | Rotas respondem 404 e saíram do menu. | Revisão da biblioteca; compilação |
| R-11 | Média | Firmware | O tempo limite de 3 min renovava a cada página vista. | Conta da abertura (`setWebPortalClientCheck(false)`). | Revisão da biblioteca |
| R-12 | Baixa | Firmware | No núcleo 3.x o número de série saía do lixo da pilha (um aparelho novo no servidor a cada boot). | MAC lido do eFuse. | Compilação |
| R-13 | Baixa | Firmware | Código digitado num portal abandonado era usado depois; botão segurado no portal podia virar o reset de 5 s. | Código descartado; o reset só conta um aperto iniciado com o loop rodando. | Compilação |
| R-14 | Baixa | Infra | Workflow com o token padrão do GitHub; API local aberta para a rede; container como root. | `permissions: {}`; `--server.address=127.0.0.1`; usuário sem privilégio no Dockerfile. | Revisão (Docker não testado aqui) |
| R-15 | Baixa | Site | `?id=` não numérico ia direto para o caminho da API. Um link forjado fazia "Excluir profissional" revogar um goniômetro, ou "Salvar paciente" sobrescrever a configuração. | `idDaUrl`: id que não é número vira "sem id". | Grupo `ids` do `tests/seguranca-front` (falha no commit anterior) |
| R-16 | Baixa | Site: goniômetro | Reconexão sem espera crescente, 401 em loop, streams órfãos ao trocar de aba. | Espera de 3 s a 60 s; 401 leva ao login; respostas de tentativas antigas descartadas. | PoC da revisão: de 12 req/15 s e 4 streams criados para 3 tentativas e 1 stream |
| R-17 | Info | Site: CSP | A CSP confiava em caminhos do jsDelivr (caminhos com `%2F` passavam pela regra). | Bibliotecas servidas pelo próprio site (`vendor/npm/`, bytes iguais aos do npm); nenhum CDN na CSP. | Grupo `estatico` (acusa arquivo adulterado) e `csp` (79/79) |
| R-18 | Info | Site + API | Token do link de recuperação ficava na barra de endereço e ia num GET (logs de acesso). | Sai da URL ao ser lido; validação por POST. | Ponta a ponta (23/23), `LinkDeRecuperacaoTest` |

## 5. Testes executados

| Conjunto | Resultado | Onde |
|---|---|---|
| API (JUnit + MockMvc, H2 em memória) | **140 passando**, 0 falhas (eram cerca de 60 antes da auditoria) | `cd rehabit-api/rehabit-api && mvn test` |
| Site (Playwright: estático, XSS em 20+ telas, ICS, CSP em todas as páginas, ids da URL) | **79 de 79** | `node tests/seguranca-front/rodar.js` |
| Ponta a ponta local (jar + site + Chromium) | **23 de 23** | `node tests/ponta-a-ponta/e2e.js` |
| Firmware | Compila sem avisos nos núcleos ESP32 2.0.9 e 3.3.2; bloco de certificados conferido (7 raízes, SHA-256 igual ao repositório da Mozilla) | `arduino-cli compile` |

**Contraprova no código antigo:**
- 18 dos 23 testes novos de fotos, campos, ângulos e tutorial falham no código de antes; os outros 5 são casos de controle.
- No site antigo, mais de 20 telas executam o script injetado; o fuzzer da revisão contou 27 execuções.
- O teste do pool falha com o OSIV ligado.
- O de limite com caminho codificado e o do código de cadastro falham sem as respectivas correções.
- O grupo `ids` falha no commit anterior à correção.

**DoS por expoente (SEC-08):** medido à parte. `setScale` sobre `1e3000000` já leva 0,7 s, e o crescimento é mais que linear: `1e999999999`, num pacote de 16 bytes, seguraria um núcleo por muitos minutos. Hoje responde 400 na hora.

## 6. Ações fora do código (necessárias)

1. **`REHABIT_JWT_SECRET` no Render, antes do deploy.** Sem ela a API não sobe em produção. Gere com `openssl rand -base64 64`.
   - Se houver qualquer chance de a produção ter rodado com o valor-padrão antigo (ele está no histórico do git e era usado quando a variável faltava), a troca é obrigatória.
   - Trocar encerra todas as sessões e exige parear de novo todos os goniômetros.
2. **Ordem do deploy:** API primeiro, depois o site. O site novo usa rotas que só a API nova tem: foto do cadastro, ingresso do SSE e validação do link por POST. O banco ganha sozinho a tabela `tb13_arquivo_enviado` e as colunas novas (`ddl-auto=update`).
3. **Firmware 2.4:** testar num aparelho antes de distribuir, depois regravar todos os aparelhos (a 2.3 aceita qualquer certificado). Conferir:
   - a cadeia de certificado da produção é aceita;
   - um aparelho novo abre o portal sozinho;
   - um aparelho configurado não abre o portal depois de REINICIAR.
4. **`img-src` do Cloudinary:** fixar em `https://res.cloudinary.com/<nome-da-conta>/` (o nome está no `CLOUDINARY_URL` do Render).
5. **Senha da conta que estava no `Rehabit.sql`:** a linha saiu do arquivo, mas continua no histórico do git. Troque essa senha onde quer que seja usada. Se o e-mail pessoal precisar sumir do histórico, é preciso reescrevê-lo.
6. **Varredura de segredos num clone completo:** este clone é raso e não inclui o histórico anterior a 23/08/2026. Use gitleaks ou trufflehog.
7. **GitHub:**
   - Deixe o `GITHUB_TOKEN` padrão como somente leitura.
   - Desative a cópia no GitHub Pages ou transforme-a em redirecionamento: ela não recebe a CSP nem o HSTS.
8. **Clínicas:** ao perder um aparelho, **Revogar** na tela Dispositivo e trocar a senha do Wi-Fi.

## 7. Riscos que permanecem e limitações

- **Produção não testada.** Tudo foi verificado localmente; não sei que variáveis o Render tem nem qual a cadeia de certificado real da API.
- **Sessão no localStorage.** Um XSS leria o token. Isso está contido pelo escape único e pela CSP sem script de terceiros; levar o token para cookie HttpOnly exigiria redesenhar a autenticação (site e API em domínios diferentes).
- **`style-src 'unsafe-inline'`** permanece; injeção de CSS só seria possível com uma injeção de HTML, e nenhuma foi encontrada.
- **Fotos em endereço público.** Os nomes são impossíveis de adivinhar (UUID ou id do Cloudinary) e os metadados são removidos, mas quem tem a URL vê a foto.
- **Goniômetro:**
  - Token (válido por um ano) e senha do Wi-Fi em texto claro na memória: criptografar a flash queima fusíveis.
  - O portal usa uma senha pública: durante os 3 minutos em que fica aberto, alguém por perto pode ler o que é digitado.
  - O certificado não tem validade nem revogação conferidas, porque o aparelho não tem relógio acertado.
- **`ddl-auto=update` em produção.** O próximo passo é passar a migrações versionadas (Flyway) e `validate`.
- **Limites em memória:** zeram quando a API reinicia e valem para uma instância só.
- **Teto global de 500 telas SSE:** muitas contas juntas podem ocupá-lo; quem passa do teto cai para o polling, mais lento, mas funcionando.
- **CORS `*`:** não é explorável com token no cabeçalho, mas restringir às origens do site é um endurecimento barato.
- **Validação de e-mail (SEC-34):** a API ainda consulta o DNS do domínio informado, com limite de tentativas.
- **Profissionais criados pela clínica** não confirmam o e-mail.
- **Não testados aqui:** o Dockerfile sem root (sem Docker) e o firmware em hardware.
