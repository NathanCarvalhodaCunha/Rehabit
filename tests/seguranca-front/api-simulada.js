// API simulada para os testes do front. Nenhum pedido sai da máquina: o
// Playwright intercepta tudo o que iria para a API de produção e responde
// daqui.
//
// Dois conjuntos de dados com a mesma forma:
//  - "benigno": o uso normal, para provar que as telas funcionam sob a CSP;
//  - "hostil": todo texto que a API devolve vira uma carga de XSS que, se
//    executar, registra o nome do campo em window.__xss, e se virar HTML
//    deixa um elemento com o atributo data-xss na página.
"use strict";

const ORIGEM_API = "https://rehabit-api-4tex.onrender.com";
const UUID = "0f8fad5b-d9cb-469f-a165-70867728950e";
const FOTO_UPLOAD = `/uploads/${UUID}.png`;
const FOTO_UPLOAD_ABSOLUTA = `${ORIGEM_API}${FOTO_UPLOAD}`;
// Na conta do Rehabit no Cloudinary, a única que o img-src do vercel.json aceita.
const FOTO_CLOUDINARY = "https://res.cloudinary.com/xuo5tlgh/image/upload/v1/perfis/foto.jpg";

/** Fecha aspas (duplas e simples), fecha a tag e injeta um <img> que avisa. */
function carga(campo) {
  return `x"'><img data-xss src=x onerror="__xss.push('${campo}')">`;
}

/** Foto que tenta sair do url('...') e do style="..." onde era colada. */
function fotoQuebrada(campo) {
  return `x');"><img data-xss src=x onerror="__xss.push('${campo}')">`;
}

const FOTO_JAVASCRIPT = "javascript:__xss.push('fotoJavascript')";

/**
 * Só aspas e atributos, sem < nem > (que o escape antigo já tratava): o caso
 * do SEC-24. A animação rh-pulse existe no CSS das telas, então o handler
 * dispara sozinho, sem clique.
 */
const NOME_QUEBRA_ATRIBUTO =
  `Dr. Ataque" data-xss="1" style="animation:rh-pulse 1ms" onanimationstart="__xss.push('optionAnimacao')`;

/** Texto para o .ics: CR solto (injeção de VALARM), CRLF, LF e os caracteres que o formato escapa. */
const OBSERVACAO_ICS =
  "Consulta\rBEGIN:VALARM\rACTION:DISPLAY\rTRIGGER:-PT1440M\rEND:VALARM\r\n" +
  "segunda linha; com vírgula, ponto-e-vírgula e barra \\ invertida\n" +
  "terceira linha bem comprida para obrigar a dobra de linha do iCalendar: ação, atenção, coração, " +
  "fisioterapêutico, órgão, ângulo — e mais um pouco de texto até passar dos 75 octetos.";

function dataRelativa(dias) {
  const d = new Date();
  d.setDate(d.getDate() + dias);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

const SESSOES = {
  clinica: { id: 2, tipo: "CLINICA", nome: "Clínica Movimento", email: "clinica@teste.example", token: "token-simulado" },
  profissional: {
    id: 6,
    tipo: "FISIOTERAPEUTA",
    nome: "Ana Ribeiro",
    email: "ana@teste.example",
    token: "token-simulado",
    nomeClinica: "Clínica Movimento",
  },
};

/** Sessão guardada no localStorage, com a foto do conjunto escolhido. */
function sessao(tipo, hostil, extras) {
  return Object.assign({}, SESSOES[tipo], { foto: hostil ? FOTO_JAVASCRIPT : FOTO_UPLOAD, tutorialVisto: true }, extras);
}

function dados(hostil, opcoes = {}) {
  const t = (campo, normal) => (hostil ? carga(campo) : normal);
  const futuro = dataRelativa(3);
  const futuro2 = dataRelativa(5);
  const passado = dataRelativa(-4);

  const fisioterapeutas = [
    {
      id: 6,
      nome: t("nomeFisio", "Ana Ribeiro"),
      especialidade: t("especialidade", "Ortopedia"),
      foto: hostil ? fotoQuebrada("fotoFisio") : FOTO_UPLOAD,
      pacientesAtivos: t("pacientesAtivos", 3),
    },
    {
      // Id como texto só no conjunto hostil: é o caso de uma API adulterada.
      id: hostil ? `7" data-xss="idColega` : 7,
      nome: "Bruno Lima",
      especialidade: null,
      foto: hostil ? FOTO_JAVASCRIPT : FOTO_CLOUDINARY,
      pacientesAtivos: 2,
    },
    { id: 8, nome: "Carla Souza", especialidade: "Esportiva", foto: FOTO_UPLOAD, pacientesAtivos: 1 },
    { id: 9, nome: "Davi Rocha", especialidade: "Neurológica", foto: FOTO_CLOUDINARY, pacientesAtivos: 0 },
  ];

  const agendamentos = opcoes.agendamentosIcs || [
    {
      id: 11,
      idPaciente: 1,
      nomePaciente: t("nomePaciente", "Maria Alves"),
      nomeFisioterapeuta: hostil ? NOME_QUEBRA_ATRIBUTO : "Ana Ribeiro",
      data: futuro,
      hora: "14:30:00",
      observacao: t("observacao", "Trazer exames"),
      status: null,
    },
    {
      id: 12,
      idPaciente: 2,
      nomePaciente: "João Pereira",
      nomeFisioterapeuta: t("nomeFisioAgenda", "Bruno Lima"),
      data: futuro2,
      hora: "09:00:00",
      observacao: null,
      status: "REMARCADA",
      dataOriginal: futuro,
      horaOriginal: "08:00:00",
    },
  ];

  const historico = [
    {
      id: 13,
      idPaciente: 1,
      nomePaciente: t("nomePacienteHistorico", "Maria Alves"),
      nomeFisioterapeuta: t("nomeFisioHistorico", "Ana Ribeiro"),
      data: passado,
      hora: "10:00:00",
      observacao: t("observacaoHistorico", "Sessão tranquila"),
      status: "REALIZADA",
    },
  ];

  const pacientes = [
    {
      id: 1,
      nome: t("nomePaciente", "Maria Alves"),
      foto: hostil ? fotoQuebrada("fotoPaciente") : FOTO_UPLOAD,
      selo: "Evoluindo",
      ultimaSessao: t("ultimaSessao", "01/10/2026"),
      status: "Ativo",
    },
    { id: 2, nome: "João Pereira", foto: FOTO_UPLOAD, selo: null, ultimaSessao: null, status: "Alta" },
  ];

  const paciente = {
    id: 1,
    nome: t("nomePaciente", "Maria Alves"),
    foto: hostil ? fotoQuebrada("fotoPaciente") : FOTO_UPLOAD,
    idade: t("idade", 34),
    sexo: t("sexo", "Feminino"),
    situacao: t("situacao", "Pós-operatório de joelho"),
    dataInicioTratamento: "2026-01-10",
    nomeFisioterapeuta: t("nomeFisio", "Ana Ribeiro"),
    idFisioterapeuta: 6,
    telefone: t("telefone", "(11) 99123-4567"),
    email: t("emailPaciente", "maria@teste.example"),
    metaAmplitude: 120,
    metaData: "2026-12-01",
    queixaPrincipal: t("queixa", "Dor ao dobrar o joelho"),
    contraindicacoes: t("contraindicacoes", "Nenhuma"),
    status: "Ativo",
    cpf: "52998224725",
    dataNascimento: "1992-03-04",
  };

  const sessoes = [
    {
      id: 9,
      data: "2026-10-01",
      hora: "10:00:00",
      duracao: t("duracao", 45),
      amplitudeMedia: 95,
      dor: t("dor", 3),
      observacoes: t("observacoesSessao", "Boa evolução"),
      temCurva: true,
    },
    { id: 8, data: "2026-09-20", hora: "10:00:00", duracao: 40, amplitudeMedia: 80, dor: 5, observacoes: null, temCurva: false },
  ];

  const desempenho = (id) => ({
    idFisioterapeuta: hostil ? carga("idDesempenho") : id,
    nomeFisioterapeuta: t("nomeDesempenho", "Ana Ribeiro"),
    especialidade: t("especialidadeDesempenho", "Ortopedia"),
    foto: hostil ? fotoQuebrada("fotoDesempenho") : FOTO_UPLOAD,
    totalSessoes: t("totalSessoes", 12),
    sessoesUltimos30Dias: t("sessoes30Dias", 4),
    totalPacientes: 3,
    pacientesAtivos: 2,
    pacientesAlta: 1,
    ganhoMedioGraus: 12,
    sessoesPorMes: [
      { rotulo: "set", sessoes: 5 },
      { rotulo: "out", sessoes: 7 },
    ],
    pacientes: [
      {
        idPaciente: hostil ? carga("idPacienteDesempenho") : 1,
        nome: t("pacienteDesempenho", "Maria Alves"),
        status: "Ativo",
        sessoes: t("sessoesPaciente", 6),
        amplitudeInicial: 60,
        amplitudeAtual: 90,
        ganho: 30,
      },
    ],
  });

  return {
    fisioterapeutas,
    fisioterapeuta: {
      id: 6,
      nome: t("nomeFisio", "Ana Ribeiro"),
      especialidade: t("especialidade", "Ortopedia"),
      coffito: t("coffito", "123456-F"),
      email: t("emailFisio", "ana@teste.example"),
      telefone: t("telefoneFisio", "(11) 98888-7777"),
      localidade: t("localidade", "Atibaia"),
      descricao: t("descricao", "Fisioterapeuta"),
      foto: hostil ? FOTO_JAVASCRIPT : FOTO_UPLOAD,
      pacientesAtivos: 3,
      sessoesEsteMes: 4,
      amplitudeMediaGeral: 90.5,
      nomeClinica: t("nomeClinica", "Clínica Movimento"),
    },
    clinica: {
      id: 2,
      nome: t("nomeClinica", "Clínica Movimento"),
      cnpj: "11222333000181",
      email: t("emailClinica", "clinica@teste.example"),
      telefone: t("telefoneClinica", "(11) 3333-4444"),
      subtitulo: t("subtitulo", "Fisioterapia"),
      endereco: t("endereco", "Rua A, 1"),
      descricao: t("descricaoClinica", "Clínica"),
      foto: hostil ? FOTO_JAVASCRIPT : FOTO_CLOUDINARY,
    },
    exclusao: { profissionais: t("exclusaoProfissionais", 4), pacientes: 3, sessoes: 12, agendamentos: 2, dispositivos: 1 },
    agendamentos,
    historico,
    pacientes,
    paciente,
    sessoes,
    curva: [
      [0, 10],
      [500, 55],
      [1000, 92],
    ],
    desempenho,
    estatisticas: [{ rotulo: t("kpiRotulo", "Pacientes"), valor: t("kpiValor", 12), detalhe: t("kpiDetalhe", "+2 no mês") }],
    alertas: [
      {
        nivel: "ATENCAO",
        idPaciente: hostil ? carga("alertaIdPaciente") : 1,
        titulo: t("alertaTitulo", "Sem sessão há 10 dias"),
        descricao: t("alertaDescricao", "Maria Alves"),
      },
    ],
    notificacoes: [
      { tipo: "NOVO_PACIENTE", lida: false, mensagem: t("notificacao", "Novo paciente"), criadaEm: new Date().toISOString() },
    ],
    busca: [
      { tipo: "PROFISSIONAL", id: 6, nome: t("buscaNome", "Ana Ribeiro"), detalhe: t("buscaDetalhe", "Ortopedia"), foto: hostil ? fotoQuebrada("fotoBusca") : FOTO_UPLOAD },
      { tipo: "PACIENTE", id: 1, nome: "Maria Alves", detalhe: "Paciente", foto: hostil ? FOTO_JAVASCRIPT : FOTO_CLOUDINARY },
      { tipo: "PACIENTE", id: 2, nome: "João Pereira", detalhe: "", foto: FOTO_UPLOAD },
    ],
    dispositivos: [{ id: hostil ? carga("idDispositivo") : 3, nome: t("nomeDispositivo", "Goniômetro 1"), ativo: true, ultimoContato: null }],
    configuracoes: { horaAbertura: "08:00:00", horaFechamento: "18:00:00", duracaoPadraoMin: 45, avisarConflito: true },
  };
}

/**
 * Monta o respondedor: (método, caminho, parâmetros) -> { status, corpo }.
 * `falhas` é uma lista de { metodo, re, mensagem } para forçar erro com a
 * mensagem escolhida (é assim que se testa a mensagem de erro na tela).
 */
function criarApi({ hostil = false, falhas = [], agendamentosIcs = null } = {}) {
  const d = dados(hostil, { agendamentosIcs });
  const ok = (corpo) => ({ status: 200, corpo });

  return function responder(metodo, caminho, parametros) {
    const alvo = caminho + (parametros ? `?${parametros}` : "");
    const falha = falhas.find((f) => (f.metodo || "GET") === metodo && f.re.test(alvo));
    if (falha) return { status: 400, corpo: { mensagem: falha.mensagem } };

    let m;
    if (metodo === "POST" && caminho === "/api/auth/login") {
      return ok(Object.assign(sessao("profissional", false), { token: "token-simulado" }));
    }
    if (metodo === "DELETE") return { status: 204, corpo: null };
    if (metodo !== "GET") return ok({});

    if (caminho === "/api/fisioterapeutas") return ok(d.fisioterapeutas);
    if ((m = /^\/api\/fisioterapeutas\/([^/]+)\/desempenho$/.exec(caminho))) return ok(d.desempenho(Number(m[1]) || 6));
    if (/^\/api\/fisioterapeutas\/[^/]+$/.test(caminho)) return ok(d.fisioterapeuta);
    if (caminho === "/api/agendamentos") return ok(d.agendamentos);
    if (caminho === "/api/agendamentos/historico" || caminho === "/api/agendamentos/realizadas") return ok(d.historico);
    if (caminho === "/api/pacientes") return ok(d.pacientes);
    if (/^\/api\/pacientes\/[^/]+$/.test(caminho)) return ok(d.paciente);
    if (/^\/api\/pacientes\/[^/]+\/sessoes$/.test(caminho)) return ok(d.sessoes);
    if (/^\/api\/pacientes\/[^/]+\/sessoes\/[^/]+\/curva$/.test(caminho)) return ok(d.curva);
    if (/^\/api\/clinicas\/[^/]+\/exclusao$/.test(caminho)) return ok(d.exclusao);
    if (/^\/api\/clinicas\/[^/]+$/.test(caminho)) return ok(d.clinica);
    if (caminho === "/api/estatisticas") return ok(d.estatisticas);
    if (caminho === "/api/alertas") return ok(d.alertas);
    if (caminho === "/api/notificacoes") return ok(d.notificacoes);
    if (caminho === "/api/busca") return ok(d.busca);
    if (caminho === "/api/dispositivos") return ok(d.dispositivos);
    if (caminho === "/api/configuracoes" || caminho === "/api/configuracoes/atendimento") return ok(d.configuracoes);
    if (caminho === "/api/goniometro/estado") return ok({ conectado: false });
    if (caminho === "/api/auth/recuperar-senha/validar") return ok({ valido: true, email: "ana@teste.example" });
    return { status: 404, corpo: { mensagem: "Rota não simulada: " + caminho } };
  };
}

module.exports = {
  ORIGEM_API,
  FOTO_UPLOAD_ABSOLUTA,
  FOTO_CLOUDINARY,
  NOME_QUEBRA_ATRIBUTO,
  OBSERVACAO_ICS,
  carga,
  criarApi,
  dataRelativa,
  sessao,
};
