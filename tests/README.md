# Testes de segurança do site

Complementam os testes da API (`rehabit-api/rehabit-api`, `mvn test`), que
cobrem autenticação, limites de tentativa, uploads e validação.

## `seguranca-front/` — XSS, CSP, SRI e exportação .ics

```bash
node tests/seguranca-front/rodar.js                 # tudo
node tests/seguranca-front/rodar.js --somente xss   # estatico, xss, ics, csp
node tests/seguranca-front/rodar.js --raiz /outra/copia/do/site
```

- **estatico**: toda biblioteca de CDN com versão fixa e `integrity`; nenhum
  script inline além do do tema (liberado por hash na CSP); nenhum `on*=` no
  HTML; cabeçalhos do `vercel.json`.
- **xss**: cada tela recebe de uma API simulada nomes, textos e fotos hostis
  (com a CSP desligada, para provar o escape e não a CSP).
- **ics**: a agenda exportada escapa CR/LF, `;`, `,` e `\`.
- **csp**: todas as páginas abertas com os cabeçalhos do `vercel.json`, sem
  violação de CSP, SRI ou Permissions-Policy e sem erro de JavaScript.

As bibliotecas da CDN vêm do registro do npm (o jsDelivr serve exatamente
esses arquivos), conferidas pelo hash publicado e guardadas em
`seguranca-front/.cache/`.

## `ponta-a-ponta/` — os fluxos que a auditoria mudou, com a API de verdade

```bash
(cd rehabit-api/rehabit-api && mvn -q package -DskipTests)
node tests/ponta-a-ponta/e2e.js
```

Sobe o jar em `localhost:8080` (banco e fotos numa pasta temporária) e o site
em `localhost:8090`, e percorre: cadastro com foto e código por e-mail, foto
sem GPS, upload sem login recusado, troca de foto apagando a antiga,
profissional com foto, SSE do goniômetro por ingresso, troca de senha com
token novo, limite de login. O navegador não resolve nenhum host além de
`localhost`.

Os dois precisam de Node 18+ e Playwright (`npm i -D playwright`, ou aponte
`PLAYWRIGHT_MODULO` para uma instalação existente).
