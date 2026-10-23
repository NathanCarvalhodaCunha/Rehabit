/* Rehabit — goniômetros pareados: lista, pareamento por código e revogação.
   A clínica e os profissionais dela pareiam (quem está com o aparelho na mão
   costuma ser o fisioterapeuta); revogar fica com a clínica. Na demonstração
   pública não se pareia nada: o aparelho dela é simulado (goniometro.js). */
(function dispositivosPareados() {
  const cartao = document.querySelector("[data-dispositivos]");
  const overlay = document.querySelector("[data-pareamento]");
  if (!cartao || !overlay) return;

  const sessao = getSessao();
  if (!sessao || (sessao.tipo !== "CLINICA" && sessao.tipo !== "FISIOTERAPEUTA")) return;
  const ehClinica = sessao.tipo === "CLINICA";

  cartao.hidden = false;

  const lista = cartao.querySelector("[data-dispositivos-lista]");
  const botaoParear = cartao.querySelector('[data-acao="parear"]');
  const campoCodigo = overlay.querySelector("[data-codigo]");
  const campoValidade = overlay.querySelector("[data-validade]");
  let contador = null;

  /* A senha da demonstração pública está no site: com um aparelho de verdade
     pareado ali, qualquer visitante mandaria Zerar ou Reiniciar para ele. A
     API recusa o pareamento (a conta é só de leitura); a tela mostra o
     aparelho simulado e explica, em vez de um botão que só daria erro. */
  if (typeof ehContaDeDemonstracao === "function" && ehContaDeDemonstracao(sessao)) {
    botaoParear.hidden = true;
    lista.innerHTML = `
      <li class="dispositivo-item">
        <div class="dispositivo-info">
          <strong>Goniômetro simulado<span class="dispositivo-selo online">Simulação</span></strong>
          <span>gerado no navegador, só nesta demonstração</span>
        </div>
      </li>`;
    const nota = document.createElement("p");
    nota.className = "dispositivos-nota";
    nota.textContent =
      "Na demonstração pública o pareamento fica desligado: a senha dela está no site, e qualquer visitante " +
      "poderia mandar comandos a um aparelho de verdade. Para parear o seu goniômetro, entre com a conta da " +
      "sua clínica ou de um profissional dela.";
    cartao.appendChild(nota);
    return;
  }

  /* A sessão do profissional não traz a clínica: o RehabitGoniometro resolve
     isso na API uma vez e guarda. */
  let idClinica = ehClinica ? sessao.id : null;
  function clinica() {
    if (idClinica != null) return Promise.resolve(idClinica);
    return RehabitGoniometro.idClinica().then((id) => (idClinica = id));
  }

  function tempoRelativo(iso) {
    if (!iso) return "nunca enviou leitura";
    const diffMin = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
    if (diffMin < 1) return "ativo agora";
    if (diffMin < 60) return `visto há ${diffMin} min`;
    const horas = Math.round(diffMin / 60);
    if (horas < 24) return `visto há ${horas}h`;
    const dias = Math.round(horas / 24);
    return dias === 1 ? "visto há 1 dia" : `visto há ${dias} dias`;
  }

  /** Considera "online" quem enviou leitura nos últimos 2 minutos. */
  function estaOnline(iso) {
    return !!iso && Date.now() - new Date(iso).getTime() < 120000;
  }

  function carregar() {
    clinica()
      .then((id) => apiGet(`/dispositivos?idClinica=${id}`))
      .then((dispositivos) => {
        if (!dispositivos.length) {
          lista.innerHTML =
            '<li class="dispositivo-vazio">Nenhum goniômetro pareado ainda. Use o botão acima para conectar o primeiro.</li>';
          return;
        }
        lista.innerHTML = dispositivos
          .map((d) => {
            const online = d.ativo && estaOnline(d.ultimoContato);
            const situacao = !d.ativo
              ? '<span class="dispositivo-selo revogado">Revogado</span>'
              : online
              ? '<span class="dispositivo-selo online">Online</span>'
              : '<span class="dispositivo-selo offline">Offline</span>';
            return `
            <li class="dispositivo-item${d.ativo ? "" : " is-revogado"}">
              <div class="dispositivo-info">
                <strong>${escaparHtml(d.nome)}${situacao}</strong>
                <span>${tempoRelativo(d.ultimoContato)}</span>
              </div>
              ${
                d.ativo && ehClinica
                  ? `<button type="button" class="btn-danger btn-pequeno" data-revogar="${escaparHtml(d.id)}">Revogar</button>`
                  : ""
              }
            </li>`;
          })
          .join("");
        if (typeof RehabitAnim !== "undefined") RehabitAnim.staggerList(lista);
      })
      .catch((err) => {
        // A API de antes deste pareamento pelo profissional (enquanto o
        // Render não publica a nova) só deixa a clínica: some com a seção,
        // como era, em vez de mostrar uma recusa.
        if (!ehClinica && err.status === 403) {
          cartao.hidden = true;
          return;
        }
        lista.innerHTML = `<li class="dispositivo-vazio">${escaparHtml(err.message)}</li>`;
      });
  }

  function pararContador() {
    if (contador) {
      clearInterval(contador);
      contador = null;
    }
  }

  function iniciarContador(expiraEm) {
    pararContador();
    const alvo = new Date(expiraEm).getTime();

    function tick() {
      const restante = Math.round((alvo - Date.now()) / 1000);
      if (restante <= 0) {
        campoValidade.textContent = "Código expirado. Gere outro.";
        campoCodigo.classList.add("is-expirado");
        pararContador();
        return;
      }
      const min = Math.floor(restante / 60);
      const seg = String(restante % 60).padStart(2, "0");
      campoValidade.textContent = `Válido por mais ${min}:${seg}`;
    }

    campoCodigo.classList.remove("is-expirado");
    tick();
    contador = setInterval(tick, 1000);
  }

  function gerarCodigo() {
    campoCodigo.textContent = "······";
    campoValidade.innerHTML = '<span class="rh-biceps" aria-hidden="true"></span>Gerando...';
    clinica()
      .then((id) => apiPost(`/dispositivos/pareamento?idClinica=${id}`, {}))
      .then((dados) => {
        // Espaço no meio para facilitar a leitura em voz alta.
        campoCodigo.textContent = dados.codigo.slice(0, 3) + " " + dados.codigo.slice(3);
        iniciarContador(dados.expiraEm);
      })
      .catch((err) => {
        campoCodigo.textContent = "------";
        campoValidade.textContent = err.message;
      });
  }

  function abrirPareamento() {
    overlay.classList.remove("is-hidden");
    gerarCodigo();
  }

  function fecharPareamento() {
    overlay.classList.add("is-hidden");
    pararContador();
    // Ao fechar, recarrega: o aparelho pode ter pareado enquanto estava aberto.
    carregar();
  }

  cartao.addEventListener("click", (e) => {
    if (e.target.closest('[data-acao="parear"]')) {
      abrirPareamento();
      return;
    }
    const revogar = e.target.closest("[data-revogar]");
    if (!revogar) return;

    revogar.disabled = true;
    apiDelete(`/dispositivos/${revogar.dataset.revogar}`)
      .then(() => {
        RehabitToast.sucesso("Dispositivo revogado. Ele não envia mais leituras.");
        carregar();
      })
      .catch((err) => {
        RehabitToast.erro(err.message);
        revogar.disabled = false;
      });
  });

  overlay.addEventListener("click", (e) => {
    if (e.target === overlay || e.target.closest('[data-acao="fechar-pareamento"]')) {
      fecharPareamento();
    } else if (e.target.closest('[data-acao="novo-codigo"]')) {
      gerarCodigo();
    }
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !overlay.classList.contains("is-hidden")) fecharPareamento();
  });

  // O botão "Atualizar agora" da tela avisa por evento; assim ele não precisa
  // alcançar esta função nem saber que esta seção existe.
  document.addEventListener("rehabit:atualizar", carregar);

  carregar();
})();
