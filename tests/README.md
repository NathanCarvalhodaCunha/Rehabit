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
- **estatico**: inclui que nenhuma página carrega script ou CSS de CDN.
- **xss**: cada tela recebe de uma API simulada nomes, textos e fotos hostis
  (com a CSP desligada, para provar o escape e não a CSP).
- **ics**: a agenda exportada escapa CR/LF, `;`, `,` e `\`.
- **csp**: todas as páginas abertas com os cabeçalhos do `vercel.json`, sem
  violação de CSP, SRI ou Permissions-Policy e sem erro de JavaScript.
- **ids**: um `?id=` que não é número (`5&x=/../../dispositivos/3`) não chega
  aos caminhos das chamadas à API.

As bibliotecas ficam em `vendor/npm/`; o teste baixa os mesmos pacotes do
registro do npm, confere cada um pelo hash publicado no registro (guardando-os
em `seguranca-front/.cache/`) e exige que o arquivo em `vendor/` seja idêntico.

## `ponta-a-ponta/` — os fluxos que a auditoria mudou, com a API de verdade

```bash
(cd rehabit-api/rehabit-api && mvn -q package -DskipTests)
node tests/ponta-a-ponta/e2e.js
```

Sobe o jar em `localhost:8080` (banco e fotos numa pasta temporária) e o site
em `localhost:8090`, e percorre: cadastro com foto e código por e-mail, foto
sem GPS, upload sem login recusado, troca de foto apagando a antiga,
profissional com foto, SSE do goniômetro por ingresso, troca de senha com
token novo, "esqueci minha senha" pelo link (token fora da URL), limite de
login. O navegador não resolve nenhum host além de
`localhost`.

Os dois precisam de Node 18+ e Playwright (`npm i -D playwright`, ou aponte
`PLAYWRIGHT_MODULO` para uma instalação existente).
