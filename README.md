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
- Sincronização de medições de um goniômetro digital (amplitude de movimento articular).
- Tema claro/escuro em todas as telas.

## Licença

Distribuído sob a licença MIT — veja [LICENSE](LICENSE).
