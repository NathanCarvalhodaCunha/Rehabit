(function editarPerfilProfissional() {
  const form = document.querySelector(".edit-grid");
  if (!form || !document.getElementById("p-coffito")) return;

  const sessao = getSessao();
  if (!sessao) return;

  // Uma clínica pode abrir esta tela para editar os dados de um dos seus
  // profissionais, vinda de perfil-profissional.html.
  const params = new URLSearchParams(window.location.search);
  const idParam = params.get("id");
  const editandoOutroProfissional = sessao.tipo === "CLINICA" && !!idParam;
  if (sessao.tipo !== "FISIOTERAPEUTA" && !editandoOutroProfissional) return;
  const idAlvo = editandoOutroProfissional ? idParam : sessao.id;

  if (editandoOutroProfissional) {
    document.querySelector(".mobile-bottomnav")?.remove();
    document.querySelectorAll('.sidebar .nav a[href="./dispositivo.html"], .sidebar .nav a[href="./configuracoes.html"]')
      .forEach((el) => el.remove());
    const homeLink = document.querySelector('.sidebar .nav a[href="./profissional.html"]');
    if (homeLink) homeLink.href = paginaTema("instituicao");
    const perfilLink = document.querySelector('.sidebar .nav a[href="./perfil-profissional.html"]');
    if (perfilLink) perfilLink.href = `${paginaTema("perfil-profissional")}?id=${idAlvo}`;

    const campoSenha = document.getElementById("p-senha-atual");
    const secaoSenha = campoSenha ? campoSenha.closest(".edit-section") : null;
    if (secaoSenha) {
      const separador = secaoSenha.previousElementSibling;
      if (separador && separador.classList.contains("edit-sep")) separador.remove();
      secaoSenha.remove();
    }
  }

  let fotoAtual = null;

  apiGet(`/fisioterapeutas/${idAlvo}`)
    .then((f) => {
      fotoAtual = f.foto;
      document.querySelector(".edit-name").textContent = f.nome;
      document.querySelector(".edit-role").textContent = f.especialidade || "";
      document.querySelector(".edit-id").textContent = `ID-${String(f.id).padStart(4, "0")}`;
      document.getElementById("p-coffito").value = f.coffito || "";
      document.getElementById("p-nome").value = f.nome || "";
      document.getElementById("p-email").value = f.email || "";
      document.getElementById("p-tel").value = f.telefone || "";
      document.getElementById("p-esp").value = f.especialidade || "";
      document.getElementById("p-loc").value = f.localidade || "";
      document.getElementById("p-desc").value = f.descricao || "";

      const foto = urlFoto(f.foto);
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

    const senhaAtual = document.getElementById("p-senha-atual")?.value || "";
    const novaSenha = document.getElementById("p-senha-nova")?.value || "";
    if (novaSenha && novaSenha.length < 6) {
      RehabitToast.erro("A nova senha deve ter ao menos 6 caracteres.");
      return;
    }
    if (novaSenha && !senhaAtual) {
      RehabitToast.erro("Informe a senha atual para trocar de senha.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";

    try {
      const atualizado = await apiPut(`/fisioterapeutas/${idAlvo}`, {
        nome: document.getElementById("p-nome").value.trim(),
        email: document.getElementById("p-email").value.trim(),
        telefone: document.getElementById("p-tel").value.trim() || null,
        especialidade: document.getElementById("p-esp").value.trim() || null,
        localidade: document.getElementById("p-loc").value.trim() || null,
        descricao: document.getElementById("p-desc").value.trim() || null,
        foto: fotoAtual,
        senhaAtual: senhaAtual || null,
        novaSenha: novaSenha || null,
      });

      if (!editandoOutroProfissional) {
        localStorage.setItem(
          "rehabit_usuario",
          JSON.stringify(Object.assign({}, sessao, {
            nome: atualizado.nome,
            email: atualizado.email,
            foto: atualizado.foto,
          }))
        );
      }

      RehabitToast.sucesso("Perfil atualizado com sucesso.");
      setTimeout(() => {
        window.location.href = editandoOutroProfissional
          ? `${paginaTema("perfil-profissional")}?id=${idAlvo}`
          : paginaTema("perfil-profissional");
      }, 1200);
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
