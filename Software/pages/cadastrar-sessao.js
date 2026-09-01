(function carregarCadastrarSessao() {
  const header = document.querySelector(".patient-header");
  if (!header) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  apiGet(`/fisioterapeutas/${sessao.id}`)
    .then((f) => fetch(`${API_BASE_URL}/goniometro/leitura?idClinica=${f.idClinica}`, {
      headers: { Authorization: `Bearer ${sessao.token}` },
    }))
    .then((r) => r.json())
    .then((dados) => {
      if (dados.angulo != null) {
        document.getElementById("s-amp").value = dados.angulo;
      }
    })
    .catch(() => {});

  const params = new URLSearchParams(window.location.search);
  const idPaciente = params.get("id");
  if (!idPaciente) {
    header.querySelector("h1").textContent = "Paciente não informado.";
    return;
  }

  document.querySelectorAll(".tabs a.tab").forEach((a) => {
    a.href = `${paginaTema("paciente")}?id=${idPaciente}`;
  });

  function formatarDataLonga(dataIso) {
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }
  function formatarDataCurta(dataIso) {
    const [, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}`;
  }

  function carregarPacienteEHistorico() {
    return Promise.all([apiGet(`/pacientes/${idPaciente}`), apiGet(`/pacientes/${idPaciente}/sessoes`)]).then(
      ([paciente, sessoes]) => {
        const idadeTexto = paciente.idade != null ? `${paciente.idade} anos` : "Idade não informada";
        const sexoTexto = paciente.sexo || "Não informado";
        const situacaoTexto = paciente.situacao || "Sem situação registrada";
        const inicioTexto = paciente.dataInicioTratamento ? formatarDataLonga(paciente.dataInicioTratamento) : "-";
        const fisioTexto = paciente.nomeFisioterapeuta || "-";

        // O cabeçalho desta tela é o mesmo da ficha do paciente, mas a foto
        // nunca era preenchida aqui: ficava sempre o círculo cinza vazio.
        const fotoPaciente = urlFoto(paciente.foto);
        const avatarEl = header.querySelector(".avatar-lg");
        if (fotoPaciente && avatarEl) {
          avatarEl.style.backgroundImage = `url("${fotoPaciente}")`;
          avatarEl.style.backgroundSize = "cover";
          avatarEl.style.backgroundPosition = "center";
        }

        header.querySelector("h1").textContent = paciente.nome;
        header.querySelector(".patient-meta.desktop-only").innerHTML =
          `${idadeTexto} – ${sexoTexto} – ${situacaoTexto}<br/>` +
          `Início do tratamento: <strong>${inicioTexto}</strong> – Fisioterapia <strong>${fisioTexto}</strong>`;
        header.querySelector(".patient-meta.mobile-only").innerHTML = `${idadeTexto} – ${sexoTexto}<br/>${situacaoTexto}`;

        const infoValores = document.querySelectorAll(".info-strip .v");
        if (infoValores[0]) infoValores[0].textContent = inicioTexto;
        if (infoValores[1]) infoValores[1].textContent = fisioTexto;

        const tbody = document.querySelector(".sessions-table tbody");
        if (!tbody) return;
        if (sessoes.length === 0) {
          tbody.innerHTML = '<tr><td colspan="4">Ainda não há sessões registradas.</td></tr>';
          return;
        }
        tbody.innerHTML = sessoes
          .map((s, i) => {
            const anterior = sessoes[i + 1];
            let avancoTexto = "0°";
            let avancoClasse = "";
            if (anterior && anterior.amplitudeMedia != null && s.amplitudeMedia != null) {
              const diferenca = Number(s.amplitudeMedia) - Number(anterior.amplitudeMedia);
              avancoTexto = `${diferenca >= 0 ? "+" : ""}${diferenca.toFixed(0)}°`;
              if (diferenca > 0) avancoClasse = "pos";
              else if (diferenca < 0) avancoClasse = "neg";
            }
            return `
              <tr>
                <td><span class="desktop-only">${formatarDataLonga(s.data)}</span><span class="mobile-only">${formatarDataCurta(s.data)}</span></td>
                <td>${s.duracao != null ? s.duracao + " min" : "-"}</td>
                <td>${s.amplitudeMedia != null ? s.amplitudeMedia + "°" : "-"}</td>
                <td class="${avancoClasse}">${avancoTexto}</td>
              </tr>`;
          })
          .join("");
        RehabitAnim.staggerList(tbody);
      }
    );
  }

  carregarPacienteEHistorico().catch((err) => {
    header.querySelector("h1").textContent = err.message;
  });

  const form = document.getElementById("cadastrarSessaoForm");
  if (!form) return;

  // Sessão é registro do que já foi atendido, então a data não pode ser
  // futura — o campo trava no dia de hoje e o envio confere de novo.
  const campoData = document.getElementById("s-data");
  const hojeIso = (() => {
    const agora = new Date();
    return (
      agora.getFullYear() +
      "-" +
      String(agora.getMonth() + 1).padStart(2, "0") +
      "-" +
      String(agora.getDate()).padStart(2, "0")
    );
  })();
  if (campoData) {
    campoData.max = hojeIso;
    if (!campoData.value) campoData.value = hojeIso;
  }

  // Espelha o valor do controle deslizante ao lado dele.
  (function ligarEscalaDeDor() {
    const controle = document.getElementById("s-dor");
    const saida = document.querySelector("[data-dor-valor]");
    if (!controle || !saida) return;
    const atualizar = () => {
      saida.textContent = controle.value;
      saida.dataset.nivel = controle.value >= 7 ? "alto" : controle.value >= 4 ? "medio" : "baixo";
    };
    controle.addEventListener("input", atualizar);
    atualizar();
  })();
  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const data = document.getElementById("s-data").value;
    const duracao = document.getElementById("s-dur").value;
    const amplitude = document.getElementById("s-amp").value;
    const campoObs = document.getElementById("s-obs");
    const observacoes = campoObs ? campoObs.value.trim() : "";
    const campoDor = document.getElementById("s-dor");

    if (!data || !duracao) {
      RehabitToast.erro("Preencha ao menos a data e a duração.");
      return;
    }
    if (data > hojeIso) {
      RehabitToast.erro("A sessão não pode ter uma data futura. Use a Agenda para marcar o que ainda vai acontecer.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";

    try {
      await apiPost(`/pacientes/${idPaciente}/sessoes`, {
        data,
        duracao: Number(duracao),
        amplitudeMedia: amplitude ? Number(amplitude) : null,
        observacoes: observacoes || null,
        dor: campoDor ? Number(campoDor.value) : null,
        idFisioterapeuta: sessao.id,
      });
      form.reset();
      if (campoData) campoData.value = hojeIso;
      await carregarPacienteEHistorico();
      RehabitToast.sucesso("Sessão cadastrada com sucesso.");
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
