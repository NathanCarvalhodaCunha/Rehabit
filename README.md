# Rehabit

Sistema de acompanhamento de fisioterapia para clínicas: cadastro de pacientes e profissionais, registro de sessões e sincronização de medições de amplitude de movimento com um goniômetro digital.

Projeto de conclusão de curso (TCC) — Ensino Médio Técnico.

## Stack

- **Frontend** — HTML, CSS e JavaScript puros (sem framework/bundler), com animações via [GSAP](https://gsap.com/). Duas áreas: `Login/` (autenticação) e `Software/` (aplicação, pós-login).
- **Backend** — [Spring Boot](https://spring.io/projects/spring-boot) 3 (Java 17), banco H2 embarcado, upload de arquivos local.

Cada tela tem uma variante de tema claro e uma escura (ex.: `login.html` / `login-escuro.html`), como páginas HTML estáticas separadas.

## Estrutura

```
Login/            Telas de autenticação (login, cadastro, esqueci a senha)
Software/         Aplicação principal (dashboard, pacientes, sessões, dispositivo, configurações)
Firmware/         Código do goniômetro (ESP32 + MPU6050) e guia de montagem
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

- Cadastro e login de clínicas e fisioterapeutas, com redefinição de senha por CNPJ/COFFITO.
- Cadastro de pacientes e vínculo com profissionais.
- Registro de sessões de fisioterapia e histórico de evolução por paciente.
- Goniômetro digital integrado, em tempo real (veja abaixo).
- Tema claro/escuro em todas as telas.

## O goniômetro em tempo real

O aparelho é um ESP32 com um MPU6050 preso ao segmento móvel da articulação.
Ele calcula o ângulo com um filtro complementar (acelerômetro + giroscópio) e
manda para a API; o navegador recebe cada leitura por **SSE**
(`GET /api/goniometro/stream`), sem ficar perguntando de tempos em tempos.
Quando o SSE não sobe — proxy que corta streaming, rede corporativa — o
cliente cai sozinho para polling e continua funcionando.

O caminho de volta usa a resposta do próprio POST de telemetria: o ESP32 não
abre porta nenhuma, só lê o que veio junto. É assim que os botões da tela
Dispositivo chegam ao aparelho (zerar/tara, identificar, iniciar e parar
captura, reiniciar) e é assim que o servidor dita o ritmo de amostragem —
10 Hz gravando, 2,5 Hz com alguém olhando, 0,5 Hz ocioso, para poupar bateria.

Na prática, dentro do sistema:

- **Tela Dispositivo** — ângulo ao vivo em um mostrador, gráfico dos últimos
  60 segundos, mínimo/máximo/amplitude, bateria, sinal Wi-Fi, número de série,
  firmware, IP e há quanto tempo chegou o último pacote.
- **Cadastrar sessão** — o mesmo canal aparece embutido no formulário: dá para
  usar o ângulo atual ou gravar o movimento completo e deixar a amplitude
  (máximo − mínimo) cair sozinha no campo, que é salvo como medição da sessão.

As leituras vivem em memória enquanto a tela está aberta — são centenas por
minuto e só interessam naquele momento. O que vai para o banco é o cadastro do
aparelho e a amplitude que o profissional escolheu gravar na sessão.

Para montar e gravar o aparelho, veja
[`Firmware/goniometro-esp32-GUIA.md`](Firmware/goniometro-esp32-GUIA.md).

## Licença

Distribuído sob a licença MIT — veja [LICENSE](LICENSE).
