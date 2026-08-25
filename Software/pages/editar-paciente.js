(function editarPaciente() {
  const form = document.querySelector(".edit-grid");
  if (!form || !document.getElementById("p-cpf")) return;

  const sessao = getSessao();
  if (!sessao) return;

  const params = new URLSearchParams(window.location.search);
  const idPaciente = params.get("id");
  if (!idPaciente) {
    RehabitToast.erro("Paciente não informado.");
    return;
  }

  let fotoAtual = null;

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

      const foto = urlFoto(p.foto);
      if (foto) {
        const avatarEl = document.querySelector(".edit-avatar-card .avatar-lg");
        avatarEl.style.backgroundImage = `url("${foto}")`;
        avatarEl.style.backgroundSize = "cover";
        avatarEl.style.backgroundPosition = "center";
      }
    })
    .catch((err) => RehabitToast.erro(err.message));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const nome = document.getElementById("p-nome").value.trim();
    if (!nome) {
      RehabitToast.erro("Preencha o nome do paciente.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";

    try {
      let foto = fotoAtual;
      if (arquivoFotoProfissional) {
        const formData = new FormData();
        formData.append("arquivo", arquivoFotoProfissional);
        const uploadResponse = await fetch(`${API_BASE_URL}/uploads`, {
          method: "POST",
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
        foto,
      });

      RehabitToast.sucesso("Paciente atualizado com sucesso.");
      setTimeout(() => {
        window.location.href = `${paginaTema("paciente")}?id=${idPaciente}`;
      }, 1200);
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
