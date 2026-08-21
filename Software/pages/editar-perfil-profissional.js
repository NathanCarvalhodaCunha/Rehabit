(function editarPerfilProfissional() {
  const form = document.querySelector(".edit-grid");
  if (!form || !document.getElementById("p-coffito")) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  let fotoAtual = null;

  apiGet(`/fisioterapeutas/${sessao.id}`)
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
    .catch((err) => alert(err.message));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const senhaAtual = document.getElementById("p-senha-atual").value;
    const novaSenha = document.getElementById("p-senha-nova").value;
    if (novaSenha && novaSenha.length < 6) {
      alert("A nova senha deve ter ao menos 6 caracteres.");
      return;
    }
    if (novaSenha && !senhaAtual) {
      alert("Informe a senha atual para trocar de senha.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";

    try {
      const atualizado = await apiPut(`/fisioterapeutas/${sessao.id}`, {
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

      localStorage.setItem(
        "rehabit_usuario",
        JSON.stringify(Object.assign({}, sessao, {
          nome: atualizado.nome,
          email: atualizado.email,
          foto: atualizado.foto,
        }))
      );

      alert("Perfil atualizado com sucesso.");
      window.location.href = paginaTema("perfil-profissional");
    } catch (err) {
      alert(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
