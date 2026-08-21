# Dados reais — instituição, profissional, paciente, sessão, dispositivo

Status: aprovado para virar plano de implementação
Data: 2026-08-20

## Contexto

O front-end (`Software/*.html`) já existe e está visualmente pronto, mas quase
todo conteúdo é fixo no HTML: nomes de fisioterapeutas, listas de pacientes,
gráficos de amplitude de movimento, status do "dispositivo" etc. O backend
(`rehabit-api`) hoje só tem `Clinica` e `Fisioterapeuta` — login, cadastro de
instituição e cadastro de profissional (com upload de foto). As tabelas
`tb03_paciente`, `tb04_goniometro`, `tb05_sessoes` e `tb06_medicao` já existem
no schema (`Rehabit.sql`) mas não têm entidade, repositório ou endpoint.

Este documento cobre o primeiro de três sub-projetos combinados com o usuário
("dados reais primeiro"; responsividade e polimento visual vêm depois, em
specs próprias). Escopo: dar suporte real de banco de dados a paciente, sessão,
medição e goniômetro, ligar os endpoints que faltam para clínica/fisioterapeuta,
e trocar dado fixo por dado real em todas as telas do `Software/`.

## Decisões já tomadas com o usuário

- **Segurança**: manter o padrão atual — todos os endpoints novos ficam
  `permitAll` no `SecurityConfig`, sem JWT. Consistente com o que já existe
  (`/api/auth/**`, `/api/fisioterapeutas/**`, `/api/uploads/**`).
- **Editar perfil**: as telas de edição passam a aceitar troca de senha
  (exigindo a senha atual), além dos campos de perfil.
- **Tela de dispositivo**: sem hardware real. Mostra o último registro de
  `tb04_goniometro` do banco; o botão "Sincronizar" cria um novo registro
  simulado (bateria aleatória, data/hora atual) em vez de falar com um
  goniômetro de verdade.

## Fora de escopo

- Autenticação JWT / proteção real de rotas.
- Integração de hardware real com o goniômetro.
- Responsividade e redesign visual (sub-projetos separados).
- `configuracoes.html` — não há dado de paciente/sessão nela; fica como está.
- `loader.html` — página de showcase/demo, não faz parte do fluxo do app.

## Modelo de dados (sem mudança de schema)

Novas entidades JPA, mapeando 1:1 as tabelas já existentes:

```
Paciente   → tb03_paciente   (id, nome, cpf, telefone, email, dataNascimento,
                               sexo, dataInicioTratamento, situacao, status,
                               idClinica, idFisioterapeuta)
Sessao     → tb05_sessoes    (id, duracao, dataSessao, horaSessao,
                               idFisioterapeuta, idPaciente)
Medicao    → tb06_medicao    (id, amplitudeMedia, dataMedicao, horaMedicao,
                               idSessao)
Goniometro → tb04_goniometro (id, bateria, dataSincronizacao, horaSincronizacao,
                               idClinica)
```

`tb03_status` guarda `"Ativo"` ou `"Inativo"`; é o campo usado para contar
"pacientes ativos". Nesta rodada todo paciente é criado com `"Ativo"` e não
existe tela para mudar isso — então "pacientes ativos" na prática é igual ao
total de pacientes por enquanto; o campo existe para quando uma tela de
inativação for construída depois. `tb03_situacao` é texto livre (ex: "Lesão
de Manguito Rotador"), preenchido no cadastro.

`Sessao` e `Medicao` são tabelas separadas no schema, mas a única tela que
cria sessão (`cadastrar-sessao.html`) pede data, duração e amplitude média
juntos — então o endpoint de criação grava as duas linhas (`Sessao` +
`Medicao`) numa transação só, e todo GET de "sessões de um paciente" devolve
os dois já combinados em um DTO (`SessaoComMedicaoDTO`).

## Endpoints novos

Todos sob `/api`, todos `permitAll`, seguindo o padrão de DTO +
`GlobalExceptionHandler` já existente.

| Método | Rota | Uso |
|---|---|---|
| GET | `/clinicas/{id}` | Perfil completo da instituição (sem senha) |
| PUT | `/clinicas/{id}` | Salvar perfil; `senhaAtual`+`novaSenha` opcionais (juntos ou nenhum dos dois) |
| GET | `/fisioterapeutas?idClinica=` | Lista de fisioterapeutas da clínica, com contagem de pacientes ativos |
| GET | `/fisioterapeutas/{id}` | Perfil completo do profissional (sem senha) |
| PUT | `/fisioterapeutas/{id}` | Salvar perfil; `senhaAtual`+`novaSenha` opcionais (juntos ou nenhum dos dois) |
| POST | `/pacientes` | Cadastrar paciente (`idFisioterapeuta` no corpo; `idClinica` é resolvido no servidor a partir do fisioterapeuta, não confiado do cliente) |
| GET | `/pacientes?idFisioterapeuta=` | Lista de pacientes do profissional, com selo derivado (Evoluindo/Estável/Instável) e data da última sessão |
| GET | `/pacientes/{id}` | Detalhe do paciente: dados + idade calculada + nome do fisioterapeuta |
| POST | `/pacientes/{id}/sessoes` | Cria sessão + medição juntas |
| GET | `/pacientes/{id}/sessoes` | Histórico de sessões do paciente (mais recente primeiro) |
| GET | `/goniometro?idClinica=` | Último registro de sincronização da clínica |
| POST | `/goniometro/sincronizar` | Cria um novo registro simulado (`idClinica` no corpo) |

O selo de status do paciente (`Evoluindo` / `Estável` / `Instável`) é
calculado comparando a amplitude média das duas sessões mais recentes —
maior → Evoluindo, menor → Instável, igual ou só uma sessão → Estável.
Paciente sem nenhuma sessão: selo omitido, front mostra "Sem sessões ainda".

## Telas e o que muda

- **instituicao.html**: lista de fisioterapeutas real (nome, especialidade,
  foto, nº de pacientes ativos), no lugar dos 5 cards fixos do "Dr. Marcelo
  da Silva".
- **profissional.html**: lista de pacientes real do profissional logado, com
  o selo derivado.
- **cadastrar-paciente.html**: formulário passa a enviar de verdade
  (`POST /pacientes`). Adiciona campo **CPF**, que falta hoje e é obrigatório
  no banco.
- **paciente.html**: cabeçalho com dados reais (idade calculada, sexo,
  situação, data de início, nome do fisioterapeuta). Gráfico de amplitude vem
  de `tb06_medicao` (últimas 5 medições). O segundo gráfico, hoje uma "taxa de
  comparecimento" inventada, vira **duração das sessões** — dado que existe
  de verdade; não há conceito de sessão agendada/faltada no schema atual.
  Tabela de sessões vem de `GET /pacientes/{id}/sessoes`.
- **cadastrar-sessao.html**: formulário liga em `POST /pacientes/{id}/sessoes`;
  campos "Data" e "Duração" passam de texto livre para `type="date"` e
  `type="number"` (minutos) — mais fácil de preencher certo.
- **Identificação de paciente na URL**: hoje nenhuma tela sabe "qual"
  paciente é — está tudo fixo. `paciente.html` e `cadastrar-sessao.html`
  passam a ler `?id=` da query string; os links que levam a elas (lista em
  `profissional.html`, link "Sessões" dentro de `paciente.html`) passam a
  incluir esse `id`.
- **perfil-instituicao.html / perfil-profissional.html**: todos os campos
  vindo do banco (via `GET /clinicas/{id}` ou `GET /fisioterapeutas/{id}`).
  As estatísticas do profissional trocam de "Adesão dos pacientes" / "Taxa
  média de evolução" (não computáveis com o schema atual) para **Pacientes
  ativos**, **Sessões este mês** e **Amplitude média geral** — todas reais.
  Na instituição, "Profissionais ativos" e "Pacientes totais" continuam
  (são reais); "Média de adesão" e "Média de evolução" trocam pelo mesmo
  motivo por **Sessões este mês** e **Amplitude média geral**, mas agregadas
  para a clínica inteira (todos os profissionais). As variações "+X este
  mês" saem (exigiriam uma segunda consulta histórica para um ganho
  pequeno; YAGNI por agora).
- **editar-perfil-instituicao.html / editar-perfil-profissional.html**:
  pré-preenchidas com os dados atuais (via GET), salvam com `PUT`. Ganham um
  campo de troca de senha (senha atual + nova senha), opcional.
- **dispositivo.html**: mostra o último `GET /goniometro?idClinica=`; o botão
  "Sincronizar" chama `POST /goniometro/sincronizar` e atualiza a tela;
  "Desconectar" continua sem efeito de backend (não há conexão real para
  desfazer).

## Tratamento de erros e estados vazios

- Paciente sem sessão: gráficos mostram um estado vazio ("Ainda não há
  sessões registradas") em vez de um SVG quebrado.
- Clínica sem fisioterapeutas / fisioterapeuta sem pacientes: lista mostra
  uma mensagem, não a tabela vazia atual.
- Erros de rede/validação seguem o padrão já usado em `Login/modal.js` e
  `Software/script.js`: `alert()` com a mensagem do backend quando disponível.
- Falha ao carregar dados de uma tela (ex: `GET /clinicas/{id}` falha): exibe
  mensagem simples no lugar do conteúdo, sem quebrar a página.

## Testes

Sem suíte de testes automatizados no projeto hoje. Verificação é manual, como
nas rodadas anteriores: subir o backend local, rodar os fluxos pelo
navegador (cadastrar paciente → cadastrar sessão → conferir gráfico e lista →
editar perfil → trocar senha → logar de novo com a senha nova), conferir as
linhas no MySQL, e limpar os dados de teste no final.
