(function editarPaciente() {
  const form = document.querySelector(".edit-grid");
  if (!form || !document.getElementById("p-cpf")) return;

  const sessao = getSessao();
  if (!sessao) return;

  const idPaciente = idDaUrl("id");
  if (!idPaciente) {
    RehabitToast.erro("Paciente não informado.");
    return;
  }

  let fotoAtual = null;

  /** Valor de um campo opcional; null quando vazio, para não gravar "". */
  function valorDe(id) {
    const el = document.getElementById(id);
    if (!el) return null;
    const valor = el.value.trim();
    return valor === "" ? null : valor;
  }

  apiGet(`/pacientes/${idPaciente}`)
    .then((p) => {
      fotoAtual = p.foto;
      document.querySelector(".edit-name").textContent = p.nome;
      document.querySelector(".edit-role").textContent = p.situacao || "";
      document.getElementById("p-cpf").value = p.cpf || "";
      document.getElementById("p-nome").value = p.nome || "";
      document.getElementById("p-nasc").value = p.dataNascimento || "";
      document.getElementById("p-sexo").value = p.sexo || "";
      document.getElementById("p-tel").value = p.telefone || "";
      document.getElementById("p-email").value = p.email || "";
      document.getElementById("p-situacao").value = p.situacao || "";
      const campoStatus = document.getElementById("p-status");
      if (campoStatus) campoStatus.value = p.status || "Ativo";

      const preencher = (id, valor) => {
        const el = document.getElementById(id);
        if (el) el.value = valor != null ? valor : "";
      };
      preencher("p-queixa", p.queixaPrincipal);
      preencher("p-historico", p.historicoClinico);
      preencher("p-medicamentos", p.medicamentos);
      preencher("p-contra", p.contraindicacoes);
      preencher("p-meta-amplitude", p.metaAmplitude);
      preencher("p-meta-data", p.metaData);

      // Só pinta com foto válida: sem ela, não apaga a prévia de um arquivo
      // que a pessoa tenha escolhido enquanto a tela carregava.
      if (urlFoto(p.foto)) aplicarFoto(document.querySelector(".edit-avatar-card .avatar-lg"), p.foto);
    })
    .catch((err) => RehabitToast.erro(err.message));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!RehabitCampos.validar(form)) return;

    const nome = document.getElementById("p-nome").value.trim();

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";
    // O loader cobre também o envio da foto, que não passa pelo apiPut; o
    // apiPut, chamado com ele já de pé, mantém o "Salvando".
    RehabitLoader.show("Salvando");

    try {
      let foto = fotoAtual;
      if (arquivoFotoProfissional) {
        const formData = new FormData();
        formData.append("arquivo", arquivoFotoProfissional);
        const uploadResponse = await buscar(`${API_BASE_URL}/uploads`, {
          method: "POST",
          // O envio de fotos exige login, como qualquer outra rota da API.
          headers: cabecalhosAutenticados(),
          body: formData,
        });
        const uploadDados = await uploadResponse.json().catch(() => ({}));
        if (!uploadResponse.ok) {
          RehabitToast.erro(uploadDados.mensagem || "Não foi possível enviar a foto de perfil.");
          return;
        }
        foto = uploadDados.url;
      }

      await apiPut(`/pacientes/${idPaciente}`, {
        nome,
        telefone: document.getElementById("p-tel").value.trim() || null,
        email: document.getElementById("p-email").value.trim() || null,
        dataNascimento: document.getElementById("p-nasc").value || null,
        sexo: document.getElementById("p-sexo").value || null,
        situacao: document.getElementById("p-situacao").value.trim() || null,
        status: document.getElementById("p-status") ? document.getElementById("p-status").value : null,
        queixaPrincipal: valorDe("p-queixa"),
        historicoClinico: valorDe("p-historico"),
        medicamentos: valorDe("p-medicamentos"),
        contraindicacoes: valorDe("p-contra"),
        metaAmplitude: valorDe("p-meta-amplitude") ? Number(valorDe("p-meta-amplitude")) : null,
        metaData: valorDe("p-meta-data"),
        foto,
      });

      RehabitToast.sucesso("Paciente atualizado com sucesso.");
      setTimeout(() => {
        window.location.href = `${paginaTema("paciente")}?id=${idPaciente}`;
      }, 1200);
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      RehabitLoader.hide();
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
