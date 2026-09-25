/* Rehabit — tela Luzes do goniômetro.

   O aparelho não tem tela. Quando ele não consegue mandar leituras, a tela
   Dispositivo só sabe dizer "offline" — quem sabe o motivo é o próprio
   aparelho, e ele diz pelas duas luzes: a verde mostra o que ele está
   fazendo, a vermelha pisca o código do erro. Sem isso, o único diagnóstico
   era ligar o cabo USB e ler o Monitor Serial.

   Tudo o que esta página mostra sai das tabelas abaixo: os cartões, as
   animações e a especificação do fim. Elas são também o contrato com o
   firmware — mudar um ritmo aqui é mudar o que o aparelho precisa fazer,
   então mude o .ino junto.
*/
(function luzesGoniometro() {
  const areaErros = document.querySelector("[data-luzes-erros]");
  if (!areaErros) return;

  const areaLegenda = document.querySelector("[data-luzes-legenda]");
  const areaTrilha = document.querySelector("[data-luzes-trilha]");
  const areaEstados = document.querySelector("[data-luzes-estados]");
  const areaTecnico = document.querySelector("[data-luzes-tecnico]");
  const botaoPausa = document.querySelector("[data-luzes-pausa]");

  // ---------------------------------------------------------------- ritmos
  //
  // Um ritmo é um ciclo de trechos que se repete: em cada trecho, quais luzes
  // ficam acesas e por quantos milissegundos.

  /* Nos erros, a vermelha pisca N vezes e fica apagada 2 s antes de repetir.
     A pausa é quase sete vezes o intervalo entre as piscadas, para ninguém
     emendar duas séries na contagem. */
  const ERRO_ACESA_MS = 300;
  const ERRO_INTERVALO_MS = 300;
  const ERRO_PAUSA_MS = 2000;

  /* A faixa de ritmo dos estados normais mostra sempre 4 s, para dar para
     comparar um com o outro; a de cada erro mostra uma série inteira. */
  const JANELA_ESTADOS_MS = 4000;

  function serieDeErro(vezes) {
    const trechos = [];
    for (let i = 1; i <= vezes; i++) {
      trechos.push({ vermelha: true, ms: ERRO_ACESA_MS });
      trechos.push({ ms: i < vezes ? ERRO_INTERVALO_MS : ERRO_PAUSA_MS });
    }
    return trechos;
  }

  function duracao(trechos) {
    return trechos.reduce((soma, trecho) => soma + trecho.ms, 0);
  }

  // ----------------------------------------------------------------- erros
  //
  // A ordem é a da preparação do aparelho ao ligar: sensor, calibração,
  // Wi-Fi, servidor, pareamento. Assim o número também diz até onde ele
  // chegou — e, com dois problemas ao mesmo tempo, o de menor número é o que
  // está mais perto da causa.

  const ERROS = [
    {
      codigo: 1,
      etapa: "Sensor",
      titulo: "Sensor não encontrado",
      resumo:
        "O aparelho não recebe resposta do sensor de movimento. Sem ele não há ângulo para medir, então o aparelho para aqui.",
      causa: "Quase sempre é um fio do sensor solto ou fora do lugar — comum depois de uma queda ou de o aparelho ser aberto.",
      passos: [
        "Desligue o aparelho e ligue de novo.",
        "Se o erro voltar, encaminhe o aparelho para manutenção: os fios do sensor precisam ser conferidos.",
      ],
      condicao: "<code>mpu.begin()</code> falhou ao ligar, ou o sensor parou de responder durante o uso.",
      serial: ["MPU6050 nao encontrado!"],
    },
    {
      codigo: 2,
      etapa: "Calibração",
      titulo: "Aparelho mexido na calibração",
      resumo:
        "Logo depois de ligar, o aparelho mede o próprio giroscópio por uns 2 segundos e, para isso, precisa ficar parado. Calibrado em movimento, o ângulo escorregaria durante a medida.",
      causa: "O aparelho foi pego ou preso no paciente logo depois de ligado.",
      passos: [
        "Apoie o aparelho numa mesa e não encoste nele.",
        "Ele tenta de novo sozinho em alguns segundos. Quando conseguir, a vermelha apaga e ele continua ligando.",
      ],
      condicao:
        "O giroscópio variou demais durante a calibração. O firmware refaz a calibração a cada poucos segundos, até o aparelho ficar parado.",
      serial: ["Aparelho mexeu na calibracao, tentando de novo..."],
    },
    {
      codigo: 3,
      etapa: "Wi-Fi",
      titulo: "Sem Wi-Fi",
      resumo: "O aparelho não encontra a rede Wi-Fi da clínica, ou a conexão caiu.",
      causa: "O roteador está desligado ou longe demais, ou o nome ou a senha da rede mudaram.",
      passos: [
        "Confira se o Wi-Fi da clínica está funcionando e aproxime o aparelho do roteador. Ele tenta reconectar sozinho a cada 10 segundos.",
        "Se o nome ou a senha da rede mudaram, segure o botão <strong>BOOT</strong> por 5 segundos para configurar tudo de novo pelo celular.",
      ],
      condicao: "Há rede salva, mas <code>WiFi.status()</code> não é <code>WL_CONNECTED</code>.",
      serial: ["Wi-Fi caiu, tentando reconectar..."],
    },
    {
      codigo: 4,
      etapa: "Servidor",
      titulo: "Servidor não responde",
      resumo: "O aparelho está no Wi-Fi, mas não consegue falar com o Rehabit.",
      causa:
        "O servidor pode estar acordando: no plano gratuito ele dorme depois de 15 minutos sem uso e leva até uns 3 minutos para voltar. Também acontece quando a rede da clínica não tem saída para a internet.",
      passos: [
        "Espere uns 3 minutos. Se era o servidor acordando, a luz verde volta sozinha.",
        "Se não voltar, confira se a rede tem internet: abra um site qualquer num celular conectado a ela.",
      ],
      condicao:
        "A última chamada ao servidor — telemetria ou pareamento — ficou sem resposta (status negativo, como tempo esgotado) ou voltou com erro 5xx.",
      serial: ["Envio falhou, status=-1"],
    },
    {
      codigo: 5,
      etapa: "Pareamento",
      titulo: "Aparelho não pareado",
      resumo: "O aparelho não tem autorização para mandar leituras à clínica.",
      causa:
        "Ele nunca foi pareado, o código digitado no portal foi recusado (errado, vencido ou já usado — cada código vale 10 minutos e uma vez só) ou o aparelho foi revogado na tela Dispositivo.",
      passos: [
        "Entre com a conta da clínica e, na tela Dispositivo, clique em <strong>Parear novo dispositivo</strong>.",
        "Segure o botão <strong>BOOT</strong> do aparelho por 5 segundos: ele apaga a configuração e volta ao modo de configuração.",
        "Conecte o celular no Wi-Fi <strong>Rehabit-Goniometro</strong> (senha rehabit123) e informe a rede da clínica, a senha dela e o código.",
      ],
      condicao: "Não há token guardado, ou o servidor respondeu 400 ao pareamento, 401 ou 403 à telemetria.",
      serial: [
        "Sem token: use o portal para parear.",
        "Pareamento falhou (status=400)",
        "Token recusado.",
        "Este aparelho foi revogado pela clinica.",
      ],
    },
  ];

  /* Não é um código — é a falta de qualquer luz. Fica junto dos erros porque
     é ali que alguém procura quando o aparelho "não faz nada". */
  const SEM_ENERGIA = {
    id: "sem-energia",
    titulo: "Nenhuma luz acesa",
    resumo: "O aparelho está desligado ou sem energia.",
    passos: [
      "Confira se o aparelho está ligado.",
      "Carregue a bateria ou ligue o cabo USB e ligue de novo: as duas luzes devem acender juntas por 1 segundo.",
    ],
    trechos: [{ ms: JANELA_ESTADOS_MS }],
  };

  // ------------------------------------------------------ estados normais

  const ESTADOS = [
    {
      id: "teste",
      nome: "Teste das luzes",
      ritmo: "As duas acesas por 1 s, uma vez, ao ligar",
      texto:
        "Mostra que nenhuma das luzes está queimada. Se o teste se repetir sozinho a cada poucos segundos, o aparelho está reiniciando sem parar — quase sempre é bateria fraca ou mau contato na alimentação.",
      trechos: [{ verde: true, vermelha: true, ms: 1000 }, { ms: 3000 }],
      tecnico: { verde: "acesa 1 s, uma vez, ao ligar", vermelha: "acesa 1 s, junto com a verde" },
    },
    {
      id: "ligando",
      nome: "Ligando",
      ritmo: "Verde pisca rápido",
      texto: "Calibrando o sensor e conectando ao Wi-Fi. Deixe o aparelho parado até a piscada mudar.",
      trechos: [{ verde: true, ms: 200 }, { ms: 200 }],
      tecnico: { verde: "200 ms acesa, 200 ms apagada" },
    },
    {
      id: "configuracao",
      nome: "Modo de configuração",
      ritmo: "Verde pisca devagar",
      texto:
        "O aparelho criou o Wi-Fi <strong>Rehabit-Goniometro</strong> e espera o celular para receber a rede da clínica e o código de pareamento. Se ele já estava configurado e caiu aqui sozinho, é porque não achou a rede salva ao ligar: confira o roteador e ligue o aparelho de novo.",
      trechos: [{ verde: true, ms: 1000 }, { ms: 1000 }],
      tecnico: { verde: "1 s acesa, 1 s apagada" },
    },
    {
      id: "pronto",
      nome: "Pronto",
      ritmo: "Verde dá uma piscada curta a cada 2 s",
      texto: "Conectado, pareado e enviando leituras. É o sinal de que está tudo certo.",
      trechos: [{ verde: true, ms: 100 }, { ms: 1900 }],
      tecnico: { verde: "100 ms acesa, 1900 ms apagada" },
    },
    {
      id: "bateria",
      nome: "Bateria fraca",
      ritmo: "Verde dá duas piscadas curtas a cada 2 s",
      texto: "Funcionando, mas com 15% de bateria ou menos. Carregue o aparelho assim que terminar o atendimento.",
      trechos: [{ verde: true, ms: 100 }, { ms: 150 }, { verde: true, ms: 100 }, { ms: 1650 }],
      tecnico: { verde: "100 ms acesa, 150 ms apagada, 100 ms acesa, 1650 ms apagada" },
    },
    {
      id: "gravando",
      nome: "Gravando",
      ritmo: "Verde acesa, sem piscar",
      texto: "Uma captura está sendo gravada pelo site. Quando ela termina, a verde volta a piscar.",
      trechos: [{ verde: true, ms: JANELA_ESTADOS_MS }],
      tecnico: { verde: "acesa enquanto a captura durar" },
    },
    {
      id: "identificando",
      nome: "Identificando",
      ritmo: "Verde e vermelha alternadas, por 4 s",
      texto:
        "Alguém clicou em <strong>Identificar aparelho</strong> na tela Dispositivo. Serve para achar qual aparelho é qual quando a clínica tem mais de um.",
      trechos: [{ verde: true, ms: 200 }, { vermelha: true, ms: 200 }],
      tecnico: { verde: "200 ms acesa, 200 ms apagada, por 4 s", vermelha: "o inverso da verde" },
    },
  ];

  const LEGENDA = [
    { verde: true, titulo: "Só a verde", texto: "Funcionando. O ritmo diz o que o aparelho está fazendo." },
    {
      vermelha: true,
      titulo: "Só a vermelha",
      texto: "Erro. Ela pisca em séries, com uma pausa de 2 s entre elas: conte as piscadas.",
    },
    {
      verde: true,
      vermelha: true,
      titulo: "As duas",
      texto: "Teste das luzes, ao ligar, ou alguém clicou em Identificar aparelho.",
    },
    { titulo: "Nenhuma", texto: "Sem energia: confira se o aparelho está ligado e com bateria." },
  ];

  const REGRAS = [
    "Vale o erro de menor número: é o mais perto da causa. Sem Wi-Fi o servidor também não responde, mas o que precisa ser resolvido é o Wi-Fi.",
    "Com a vermelha piscando, a verde fica apagada.",
    "Ao trocar de erro, termine a série e a pausa em andamento antes de começar a nova — senão a contagem mistura dois códigos.",
    "Comande as luzes por um temporizador (<code>Ticker</code>) ou por uma tarefa própria, não pelo <code>loop()</code>: o portal de configuração e os envios HTTP seguram o loop por vários segundos e congelariam a piscada no meio.",
    "Se o pareamento ficar sem resposta do servidor, guarde o código e tente de novo a cada 10 s enquanto ele valer (10 minutos). Sem isso, o erro 4 não some sozinho quando o servidor acorda.",
    "Bateria fraca é 15% ou menos — o mesmo limite em que a barra de bateria da tela Dispositivo fica vermelha. Sem o divisor de bateria montado, esse aviso nunca aparece.",
  ];

  const ICONE_CHECK =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>';

  function prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  // --------------------------------------------------------------- desenho

  function face(luzes, mini) {
    const acesa = (luz) => (luzes && luzes[luz] ? " acesa" : "");
    return `
      <span class="luz-face${mini ? " mini" : ""}">
        <span class="luz-slot"><span class="luz-led verde${acesa("verde")}"><span class="luz-brilho"></span></span><span class="luz-rotulo">verde</span></span>
        <span class="luz-slot"><span class="luz-led vermelha${acesa("vermelha")}"><span class="luz-brilho"></span></span><span class="luz-rotulo">vermelha</span></span>
      </span>`;
  }

  /** Luzes que acendem em algum momento do ciclo; nenhuma vira uma faixa toda apagada. */
  function luzesUsadas(trechos) {
    const usadas = ["verde", "vermelha"].filter((luz) => trechos.some((t) => t[luz]));
    return usadas.length ? usadas : ["nenhuma"];
  }

  function faixa(trechos, luz, vezes) {
    let partes = "";
    for (let i = 0; i < vezes; i++) {
      trechos.forEach((t) => {
        partes += `<span class="ritmo-trecho${t[luz] ? " is-acesa" : ""}" style="flex-grow:${t.ms}"></span>`;
      });
    }
    return `<span class="ritmo-faixa ${luz}">${partes}</span>`;
  }

  /* Cada demonstração fica registrada aqui para a animação saber o ritmo
     dela depois que o HTML entrar na página. */
  const demos = [];

  function demo(trechos, vezesNaFaixa, descricao) {
    const indice = demos.push({ trechos, vezesNaFaixa }) - 1;
    const faixas = luzesUsadas(trechos)
      .map((luz) => faixa(trechos, luz, vezesNaFaixa))
      .join("");
    return `
      <div class="luz-demo" role="img" aria-label="${descricao}" data-demo="${indice}">
        ${face()}
        <span class="ritmo">${faixas}<span class="ritmo-cursor"></span></span>
      </div>`;
  }

  function vezesNaJanela(trechos) {
    return Math.max(1, Math.round(JANELA_ESTADOS_MS / duracao(trechos)));
  }

  function listaDePassos(passos) {
    return `<h4>O que fazer</h4><ol class="luz-passos">${passos.map((p) => `<li>${p}</li>`).join("")}</ol>`;
  }

  function cartaoDeErro(erro) {
    const piscadas = erro.codigo === 1 ? "1 piscada" : `${erro.codigo} piscadas`;
    const descricao = `Luz vermelha pisca ${erro.codigo === 1 ? "1 vez" : `${erro.codigo} vezes`}, apaga por 2 segundos e repete. Luz verde apagada.`;
    return `
      <article class="card luz-card" id="erro-${erro.codigo}" tabindex="-1" aria-labelledby="erro-${erro.codigo}-titulo">
        ${demo(serieDeErro(erro.codigo), 1, descricao)}
        <div class="luz-texto">
          <p class="luz-codigo">Erro ${erro.codigo} · ${piscadas}</p>
          <h3 id="erro-${erro.codigo}-titulo">${erro.titulo}</h3>
          <p>${erro.resumo}</p>
          <p class="luz-causa">${erro.causa}</p>
          ${listaDePassos(erro.passos)}
        </div>
      </article>`;
  }

  function cartaoSemEnergia() {
    return `
      <article class="card luz-card" id="${SEM_ENERGIA.id}" tabindex="-1" aria-labelledby="${SEM_ENERGIA.id}-titulo">
        ${demo(SEM_ENERGIA.trechos, 1, "As duas luzes apagadas.")}
        <div class="luz-texto">
          <p class="luz-codigo neutro">Sem código · tudo apagado</p>
          <h3 id="${SEM_ENERGIA.id}-titulo">${SEM_ENERGIA.titulo}</h3>
          <p>${SEM_ENERGIA.resumo}</p>
          ${listaDePassos(SEM_ENERGIA.passos)}
        </div>
      </article>`;
  }

  function linhaDeEstado(estado) {
    return `
      <li class="luz-estado" id="luz-${estado.id}" tabindex="-1">
        ${demo(estado.trechos, vezesNaJanela(estado.trechos), `${estado.ritmo}.`)}
        <div class="luz-texto">
          <h3>${estado.nome}</h3>
          <p class="luz-ritmo-nome">${estado.ritmo}</p>
          <p>${estado.texto}</p>
        </div>
      </li>`;
  }

  function tabela(cabecalho, linhas) {
    return `
      <div class="tabela-rolavel">
        <table class="luzes-tabela">
          <thead><tr>${cabecalho.map((c) => `<th>${c}</th>`).join("")}</tr></thead>
          <tbody>${linhas.map((l) => `<tr>${l.map((c) => `<td>${c}</td>`).join("")}</tr>`).join("")}</tbody>
        </table>
      </div>`;
  }

  function especificacao() {
    const ritmoDeErro =
      `N piscadas de ${ERRO_ACESA_MS} ms, com ${ERRO_INTERVALO_MS} ms apagada entre elas ` +
      `e ${ERRO_PAUSA_MS / 1000} s apagada depois da última (N é o código)`;
    return `
      <p>Os tempos abaixo são os mesmos que animam esta página: o que aparece aqui é exatamente o que o aparelho deve fazer.</p>

      <h4>Ligação</h4>
      <p>Verde no <code>GPIO19</code> e vermelha no <code>GPIO18</code>, cada uma com um resistor de 220 Ω até o GND. Monte a verde à esquerda da vermelha: quem não distingue as duas cores reconhece a luz pela posição. Evite o <code>GPIO2</code>, que é pino de boot (strapping) — um LED ali pode atrapalhar a gravação do firmware.</p>

      <h4>Ritmos</h4>
      ${tabela(
        ["Sinal", "Verde", "Vermelha"],
        [["Erros 1 a 5", "apagada", ritmoDeErro]].concat(
          ESTADOS.map((e) => [e.nome, e.tecnico.verde || "apagada", e.tecnico.vermelha || "apagada"])
        )
      )}

      <h4>Quando cada erro acende</h4>
      ${tabela(
        ["Código", "Condição no firmware", "Monitor Serial"],
        ERROS.map((e) => [
          `<strong>${e.codigo}</strong>`,
          e.condicao,
          e.serial.map((s) => `<code>${s}</code>`).join("<br />"),
        ])
      )}

      <h4>Regras</h4>
      <ul class="luzes-regras">${REGRAS.map((r) => `<li>${r}</li>`).join("")}</ul>`;
  }

  if (areaLegenda) {
    areaLegenda.innerHTML = LEGENDA.map(
      (item) => `
      <li>
        ${face(item, true)}
        <span><strong>${item.titulo}</strong><span class="luzes-legenda-texto">${item.texto}</span></span>
      </li>`
    ).join("");
  }

  if (areaTrilha) {
    areaTrilha.innerHTML =
      ERROS.map(
        (e) => `
        <li><a href="#erro-${e.codigo}" data-ir="erro-${e.codigo}" aria-label="${e.codigo} ${e.etapa}: ${e.titulo}">
          <span class="trilha-num">${e.codigo}</span>
          <span class="trilha-nome">${e.etapa}</span>
        </a></li>`
      ).join("") +
      `
        <li class="is-pronto"><a href="#luz-pronto" data-ir="luz-pronto" aria-label="Pronto: funcionando">
          <span class="trilha-num">${ICONE_CHECK}</span>
          <span class="trilha-nome">Pronto</span>
        </a></li>`;
  }

  areaErros.innerHTML = ERROS.map(cartaoDeErro).join("") + cartaoSemEnergia();
  if (areaEstados) areaEstados.innerHTML = ESTADOS.map(linhaDeEstado).join("");
  if (areaTecnico) areaTecnico.innerHTML = especificacao();

  // ------------------------------------------------------------- animação

  const animacoes = [];

  /* Quadros de uma luz no ciclo. O "steps(1, end)" segura o valor até o
     quadro seguinte: é um LED, não um dimmer — ele não passa por meio-termo. */
  function quadros(trechos, luz) {
    const total = duracao(trechos);
    const lista = [];
    let decorrido = 0;
    trechos.forEach((t) => {
      lista.push({ offset: decorrido / total, opacity: t[luz] ? 1 : 0, easing: "steps(1, end)" });
      decorrido += t.ms;
    });
    lista.push({ offset: 1, opacity: trechos[trechos.length - 1][luz] ? 1 : 0 });
    return lista;
  }

  function animar(elemento, trechos, vezesNaFaixa) {
    const ciclo = duracao(trechos);
    ["verde", "vermelha"].forEach((luz) => {
      if (!trechos.some((t) => t[luz])) return; // apagada o ciclo todo
      const brilho = elemento.querySelector(`.luz-led.${luz} .luz-brilho`);
      animacoes.push(brilho.animate(quadros(trechos, luz), { duration: ciclo, iterations: Infinity }));
    });
    /* O cursor percorre a faixa no mesmo relógio da luz: a faixa tem um
       número inteiro de ciclos, então os dois nunca se desencontram. */
    const cursor = elemento.querySelector(".ritmo-cursor");
    animacoes.push(
      cursor.animate([{ transform: "translateX(0)" }, { transform: "translateX(100%)" }], {
        duration: ciclo * vezesNaFaixa,
        iterations: Infinity,
      })
    );
  }

  let pausado = prefersReducedMotion();

  function aplicarPausa() {
    animacoes.forEach((a) => (pausado ? a.pause() : a.play()));
    if (botaoPausa) botaoPausa.textContent = pausado ? "Animar as luzes" : "Pausar as luzes";
  }

  if (typeof Element.prototype.animate === "function") {
    document.querySelectorAll("[data-demo]").forEach((el) => {
      const registro = demos[Number(el.dataset.demo)];
      animar(el, registro.trechos, registro.vezesNaFaixa);
    });
    // Com movimento reduzido, começa parado: cada luz fica no primeiro quadro
    // (acesa) e a faixa ao lado mostra o ritmo inteiro.
    aplicarPausa();
    if (botaoPausa) {
      botaoPausa.addEventListener("click", () => {
        pausado = !pausado;
        aplicarPausa();
      });
    }
  } else if (botaoPausa) {
    botaoPausa.hidden = true;
  }

  // ------------------------------------------------------------ navegação

  let destacado = null;
  let timerDestaque = null;

  /* A trilha rola até o cartão sem mexer no endereço: cada clique num link
     com "#" viraria uma entrada no histórico, e o "voltar" do celular
     passaria a andar de cartão em cartão em vez de sair da tela. */
  function irPara(id) {
    const alvo = document.getElementById(id);
    if (!alvo) return;
    alvo.scrollIntoView({ behavior: prefersReducedMotion() ? "auto" : "smooth", block: "start" });
    alvo.focus({ preventScroll: true });
    if (destacado) destacado.classList.remove("is-destaque");
    clearTimeout(timerDestaque);
    destacado = alvo;
    alvo.classList.add("is-destaque");
    timerDestaque = setTimeout(() => alvo.classList.remove("is-destaque"), 2400);
  }

  if (areaTrilha) {
    areaTrilha.addEventListener("click", (e) => {
      const link = e.target.closest("[data-ir]");
      if (!link) return;
      e.preventDefault();
      irPara(link.dataset.ir);
    });
  }
})();
